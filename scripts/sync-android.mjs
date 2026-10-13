import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const files = ['记录.html', '离线模型/labels-zh.js', '离线模型/ort.wasm.min.js', '离线模型/wasm-code.js', '离线模型/wasm-binary.js', '离线模型/model-u2.js', '离线模型/model-mobilenet.js', '离线模型/LICENSE-Chinese-Labels.txt', '离线模型/LICENSE-ONNX-Runtime.txt', '离线模型/LICENSE-U2Net.txt', '离线模型/来源与许可.md', '参考字体/Huiwen-mincho.ttf', '参考字体/汇文明朝体-授权说明.txt'];
for (const file of files) if (!fs.existsSync(path.join(root, 'web', file))) throw new Error(`Missing ${file}; run prepare-assets.ps1 first.`);
for (const file of files) {
  const destination = path.join(root, 'android/app/src/main/assets', file);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(path.join(root, 'web', file), destination);
}
console.log(`Synchronized ${files.length} files from public-source web edition.`);
