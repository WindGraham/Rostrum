# VSCode Extension Host Assets

此目录包含VSCode Extension Host的编译后的JavaScript文件。

## 文件说明

- `extensionHostWorker.js` - VSCode Extension Host Worker的编译后的JavaScript文件

## 如何生成文件

1. 进入vscode-main目录
2. 运行: `npm install`
3. 运行: `npm run compile`
4. 将编译后的文件复制到此目录:
   ```
   copy vscode-main\out\vs\workbench\api\worker\extensionHostWorker.js app\src\main\assets\vscode_extension_host\
   ```

## 注意事项

- 文件会在应用首次启动时自动从assets复制到filesDir
- 如果文件不存在，Extension Host会使用占位实现
- 文件会被打包到APK中，无需用户手动操作

