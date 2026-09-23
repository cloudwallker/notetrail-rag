# NoteTrail RAG v0.1

Java 21 独立学习型知识库，覆盖导入、分块、检索、带出处的回答与历史记录。全新实现，不读取或复制其他平台代码、配置、素材。

## 栈与边界

Spring Boot 3.5.11、Web、Validation、JDBC、文件型 H2、Lombok、JUnit。默认监听127.0.0.1:18082，NOTETRAIL_DB_URL覆盖数据库地址，默认jdbc:h2:file:./data/notetrail。不要依赖向量数据库、Kafka、OCR或付费API。默认是可解释的摘录式回答，不冒充大模型生成。

## 接口契约

- GET /api/health -> {status:"UP",service:"notetrail-rag"}
- POST /api/documents，请求 {title,text} -> 201 Document。
- GET /api/documents -> Document[]；DELETE /api/documents/{id} -> 204，并删除文档所属块。
- Document = {id,title,chunkCount,createdAt}。
- POST /api/search，请求 {query,topK} -> {hits:[Hit]}。
- Hit = {documentId,title,chunkId,position,text,score}，position 从0开始。
- POST /api/questions，请求 {question,topK} -> Answer。
- GET /api/questions -> 最近50个Answer，按时间降序。
- Answer = {id,question,answer,mode,citations:[Hit],createdAt}。
- mode = EXTRACTIVE 或 OPENAI_COMPATIBLE。没有证据时不调用模型，mode EXTRACTIVE，answer明确无相关资料，citations为空。
- 错误统一 {code,message}，参数400、缺失404、模型失败502，其余500；不暴露密钥、原始请求或供应商错误正文。

## 文档与检索

title非空最大100字符；text非空最大100000 Unicode码点；JSON文本导入支持浏览器读取UTF-8 txt/md，不做PDF/OCR。每文档按500码点分块、重叠80码点，保留块顺序，不切断代理对。空白文档拒绝；保存文档和块在同一事务；删除块与文档同一事务。
查询非空，最长1000字符；topK缺省5，范围1–10，非整数拒绝。检索对英文做小写词分割，中文按汉字单字与连续双字切分；用BM25排序（k1=1.2,b=0.75），仅保留score>0，分数相同时按documentId及position稳定排序。向客户端返回真实片段与文档出处，不宣称向量语义检索。
摘录回答用[1]、[2]编号拼接检索片段，citations与编号顺序一致；无命中明确回答资料不足。历史持久化保存回答与引用快照，即使原文档删除也不伪装成当前仍存在的文档。

## 可选真实生成

NOTETRAIL_AI_MODE=local（默认）或openai；openai模式需要NOTETRAIL_AI_BASE_URL（例如https://example.com/v1）、NOTETRAIL_AI_MODEL、NOTETRAIL_AI_API_KEY。不得内置真实密钥或固定商业供应商。
将问题和已检索证据发送到 baseUrl + /chat/completions，messages包含“只依据所给材料回答、使用[编号]引用、资料不足时说明”，温度0；真实网络调用用JDK HttpClient，完整响应超时15秒、正文最大256KiB、禁止重定向。
模型异常502，不静默降级；未配置完整参数也给出清晰错误，不回显secret。模式、生成内容和检索引用一并持久化，UI必须标出模式。引用表示提供给模型的证据，不保证模型生成内容必然正确。
