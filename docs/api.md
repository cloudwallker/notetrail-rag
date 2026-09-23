# NoteTrail RAG API

服务默认监听 `http://127.0.0.1:18082`，所有请求和响应均使用 UTF-8 JSON。时间字段是 UTC ISO-8601 字符串。

## 健康检查

`GET /api/health`

```json
{"status":"UP","service":"notetrail-rag"}
```

## 文档

`POST /api/documents`

```json
{"title":"Spring 笔记","text":"Spring Boot 可以使用 JDBC 保存数据。"}
```

成功返回 `201 Created`，并设置 `Location: /api/documents/{id}`。

```json
{
  "id": 1,
  "title": "Spring 笔记",
  "chunkCount": 1,
  "createdAt": "2026-09-16T05:00:00Z"
}
```

`GET /api/documents` 返回按 `id` 升序排列的文档数组。

`DELETE /api/documents/{id}` 删除文档及其检索块，成功返回 `204 No Content`；不存在的文档返回 `404`。删除不会改写已经保存的问答引用快照。

`title` 必须是字符串，去除首尾空白后非空，最多 100 个 Unicode 码点。`text` 必须是非空字符串，最多 100000 个 Unicode 码点。正文按 500 个码点分块，相邻块重叠 80 个码点。

## 检索

`POST /api/search`

```json
{"query":"spring 数据库","topK":5}
```

```json
{
  "hits": [
    {
      "documentId": 1,
      "title": "Spring 笔记",
      "chunkId": 1,
      "position": 0,
      "text": "Spring Boot 可以使用 JDBC 保存数据。",
      "score": 1.3862943611198906
    }
  ]
}
```

`query` 必须是非空字符串，最多 1000 个 Unicode 码点。`topK` 可省略，默认值为 5；提供时必须是 JSON 整数且范围为 1 到 10，字符串、布尔值和小数均会返回 `400`。

检索对英文词做小写归一化，对连续中文生成汉字单字和相邻双字词元。结果使用 BM25（`k1=1.2`、`b=0.75`）排序，只返回正分结果；同分时按 `documentId`、`position` 升序排列。

## 问答与历史

`POST /api/questions`

```json
{"question":"怎样保存数据？","topK":3}
```

默认 `local` 模式返回检索片段组成的摘录式回答：

```json
{
  "id": 1,
  "question": "怎样保存数据？",
  "answer": "[1] Spring Boot 可以使用 JDBC 保存数据。",
  "mode": "EXTRACTIVE",
  "citations": [
    {
      "documentId": 1,
      "title": "Spring 笔记",
      "chunkId": 1,
      "position": 0,
      "text": "Spring Boot 可以使用 JDBC 保存数据。",
      "score": 0.28768207245178085
    }
  ],
  "createdAt": "2026-09-16T05:01:00Z"
}
```

没有命中时返回 `mode: "EXTRACTIVE"`、`answer: "未找到与问题相关的资料。"` 和空 `citations`，并且不会调用模型。

启用兼容模型后，成功回答的 `mode` 为 `OPENAI_COMPATIBLE`。`citations` 表示提供给模型的检索证据，不保证模型生成内容必然正确。服务会将证据标记为不可信引用资料，并要求模型不要执行资料中的操作指令。

`GET /api/questions` 返回最近 50 条问答，按创建时间和 `id` 倒序排列。回答、模式和引用均以快照形式保存。

## 可选 OpenAI 兼容模式

将 `NOTETRAIL_AI_MODE` 设为 `openai`，并配置 `NOTETRAIL_AI_BASE_URL`、`NOTETRAIL_AI_MODEL` 和 `NOTETRAIL_AI_API_KEY`。服务向 `{baseUrl}/chat/completions` 发送问题与完整检索片段，温度为 0。完整响应总超时为 15 秒，响应正文上限为 256 KiB，且不跟随重定向。

模型非 2xx、超时、过大、畸形响应或配置不完整均返回安全的 `502`，不会把密钥或供应商响应正文写入 API 响应。

## 错误

所有错误使用统一结构：

```json
{"code":"VALIDATION_ERROR","message":"请求参数或 JSON 格式无效。"}
```

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 参数、类型、范围或 JSON 格式无效 |
| 404 | `NOT_FOUND` | 文档不存在 |
| 502 | `AI_UPSTREAM_ERROR` | 模型配置或上游调用失败 |
| 500 | `INTERNAL_ERROR` | 未预期的服务内部错误 |

请求体必须是与接口匹配的 JSON 对象。畸形 JSON、顶层 `null`、顶层标量、重复字段和未知字段均返回 `400`。
