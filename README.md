# NoteTrail RAG

**中文简介：** Java 21 知识库应用，提供 Unicode 文本分块、中英文 BM25 检索、来源引用与持久化问答历史。

**English overview:** A Java 21 knowledge-base application with Unicode text chunking, Chinese and English BM25 search, source citations, and persistent question history.

## 使用 / Usage

本地摘录回答可离线运行；兼容 OpenAI 的模型服务为可选配置。Windows 可运行 `start.bat`。

Local extractive answers work offline; an OpenAI-compatible model service is optional. On Windows, run `start.bat`.

```text
mvn package -DskipTests
java -jar target/notetrail-rag.jar
```

## 许可 / License

见 [LICENSE](LICENSE)。 / See [LICENSE](LICENSE).
