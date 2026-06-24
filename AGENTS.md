# 项目规则

## 代理配置
- 所有 GitHub 操作（git push、gh 命令）必须使用 `proxychains4`
- 示例：`proxychains4 git push`

## 开发循环
1. 改代码
2. proxychains4 git add . && proxychains4 git commit -m "xxx"
3. proxychains4 git push
4. proxychains4 gh run watch
5. proxychains4 gh run download --name app-debug
6. adb install -r app-debug.apk

## Git 提交规范
- **不允许添加协作者信息**：提交时禁止使用 `Co-Authored-By` 行，不要在提交信息中添加任何 AI 协作者信息。

## 项目结构
autodial/
├── server/    — Node.js WS 服务器
├── web/       — PC 网页
├── android/   — Kotlin 项目
└── .github/workflows/build.yml — Actions 打包

## ADB 连接
- 无线 ADB: adb connect 192.168.1.8:40913
