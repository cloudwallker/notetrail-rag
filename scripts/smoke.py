import json
import os
from pathlib import Path
import socket
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(port, path, body=None, method=None, expected=200):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        f"http://127.0.0.1:{port}{path}", data=data,
        headers={"Content-Type": "application/json"},
        method=method or ("POST" if body is not None else "GET"),
    )
    try:
        response = OPENER.open(request, timeout=35)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        assert response.status == expected, (path, response.status, raw.decode("utf-8", "replace"))
        if not raw:
            return None
        if "application/json" in response.headers.get("Content-Type", ""):
            return json.loads(raw)
        return raw.decode("utf-8")


class Service:
    def __init__(self, directory, name):
        self.directory = directory
        self.name = name
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            self.port = sock.getsockname()[1]
        java_home = os.environ.get("JAVA_HOME")
        self.java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else "java"
        self.process = None
        self.log = None

    def start(self):
        self.log = (self.directory / "smoke.log").open("ab")
        env = os.environ.copy()
        env["NOTETRAIL_AI_MODE"] = "local"
        for key in ("FLOWTRAIL_DB_URL", "NOTETRAIL_DB_URL", "SPRING_DATASOURCE_URL"):
            env.pop(key, None)
        self.process = subprocess.Popen(
            [self.java, "-Dfile.encoding=UTF-8", "-jar", str(self.directory / "lib" / (self.name + ".jar")),
             "--server.address=127.0.0.1", f"--server.port={self.port}"],
            cwd=self.directory, stdout=self.log, stderr=subprocess.STDOUT, env=env,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise AssertionError("Service stopped during startup; inspect " + str(self.directory / "smoke.log"))
            try:
                if call(self.port, "/api/health")["status"] == "UP":
                    return
            except (OSError, AssertionError):
                pass
            time.sleep(0.15)
        raise AssertionError("Service startup timed out")

    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=10)
        if self.log:
            self.log.close()


def main():
    name = SERVICE_NAME
    archive = ROOT / "target" / (name + "-dist.zip")
    assert archive.is_file(), "Run mvn clean verify first"
    with tempfile.TemporaryDirectory(prefix="service-smoke-", dir=ROOT / "target") as temporary:
        with zipfile.ZipFile(archive) as source:
            assert source.testzip() is None
            source.extractall(temporary)
        directory = next(path for path in Path(temporary).iterdir() if path.is_dir())
        service = Service(directory, name)
        try:
            service.start()
            page = call(service.port, "/")
            assert "<!doctype html>" in page.lower()
            assert "/api/" in call(service.port, "/app.js")
            exercise(service)
            report = {"service": name, "packagedApi": "passed", "restartPersistence": "passed", "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S")}
            (ROOT / "target" / "smoke-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(json.dumps(report, ensure_ascii=False))
        except Exception:
            service.stop()
            log = directory / "smoke.log"
            if log.exists():
                shutil.copyfile(log, ROOT / "target" / "smoke-failure.log")
            raise
        finally:
            service.stop()


SERVICE_NAME = "notetrail-rag"


def exercise(service):
    port = service.port
    document = json.loads((ROOT / "examples" / "java-notes.json").read_text(encoding="utf-8"))
    created = call(port, "/api/documents", document, expected=201)
    assert created["chunkCount"] >= 1
    query = {"query": "虚拟线程", "topK": 3}
    hits = call(port, "/api/search", query)["hits"]
    assert hits and hits[0]["documentId"] == created["id"], hits
    answer = call(port, "/api/questions", {"question": "虚拟线程适合什么场景", "topK": 3})
    assert answer["mode"] == "EXTRACTIVE" and answer["citations"]
    assert "[1]" in answer["answer"]
    empty = call(port, "/api/questions", {"question": "zzqunrelatedtoken98765"})
    assert not empty["citations"]
    call(port, "/api/search", {"query": "虚拟线程", "topK": 0}, expected=400)
    service.stop()
    service.start()
    assert any(doc["id"] == created["id"] for doc in call(port, "/api/documents"))
    assert call(port, "/api/search", query)["hits"]
    call(port, "/api/documents/" + str(created["id"]), method="DELETE", expected=204)
    assert not call(port, "/api/search", query)["hits"]
    snapshots = call(port, "/api/questions")
    assert any(value["id"] == answer["id"] and value["citations"] for value in snapshots)


if __name__ == "__main__":
    main()
