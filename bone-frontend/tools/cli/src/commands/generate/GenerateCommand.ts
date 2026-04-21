/**
 * @fileoverview 代码生成命令
 * 从后端 codegen 服务生成前端 CRUD 页面代码
 * 遵循 React 18 + TypeScript + Ant Design Pro 最佳实践
 */
import { Command } from 'commander';
import chalk from 'chalk';
import ora from 'ora';
import * as fs from 'fs';
import * as path from 'path';
import * as https from 'https';
import * as http from 'http';
import * as unzipper from 'unzipper';
import { promisify } from 'util';

const mkdir = promisify(fs.mkdir);
const exists = promisify(fs.exists);

/**
 * 生成命令类
 * 提供从后端 codegen 服务下载生成的前端代码并解压到指定目录
 */
export class GenerateCommand {
  private command: Command;

  constructor() {
    this.command = new Command('generate');
    this.setup();
  }

  public getCommand(): Command {
    return this.command;
  }

  private setup(): void {
    this.command
      .description('Generate frontend CRUD code from backend codegen service')
      .option('-h, --host <host>', 'Backend codegen service host', 'http://localhost:8080')
      .option('-p, --port <port>', 'Backend codegen service port', '8080')
      .option('-t, --table-ids <ids>', 'Table IDs to generate (comma separated)', '')
      .option('-g, --groupId <groupId>', 'Group ID for generation', 'default')
      .option('-m, --modelType <type>', 'Model type (1=SAAS, 2=DDD)', '1')
      .option('-o, --output <dir>', 'Output directory for generated files', './src')
      .action(async (options: any) => {
        try {
          await this.execute(options);
        } catch (error) {
          console.error(chalk.red('Code generation failed:'), error);
          process.exit(1);
        }
      });
  }

  /**
   * 执行代码生成
   */
  private async execute(options: {
    host: string;
    port: string;
    tableIds: string;
    groupId: string;
    modelType: string;
    output: string;
  }): Promise<void> {
    const { host, port, tableIds, groupId, modelType, output } = options;

    // 验证参数
    if (!tableIds || tableIds.trim().length === 0) {
      console.error(chalk.red('Error: table-ids is required'));
      this.command.help();
      process.exit(1);
    }

    const spinner = ora('Starting code generation...').start();

    try {
      // 构建请求 URL
      const tableIdList = tableIds.split(',').map((id) => id.trim()).join(',');
      const url = `${host.replace(/\/$/, '')}:${port}/api/v1/code-generation/generate/batch?tableIds=${tableIdList}&groupId=${groupId}&modelType=${modelType}`;

      spinner.text = `Downloading generated code from ${url}...`;

      // 下载 ZIP
      const zipBuffer = await this.downloadZip(url);

      spinner.text = 'Extracting generated files...';

      // 确保输出目录存在
      const outputDir = path.resolve(process.cwd(), output);
      if (!await exists(outputDir)) {
        await mkdir(outputDir, { recursive: true });
      }

      // 解压
      await this.extractZip(zipBuffer, outputDir);

      spinner.succeed(chalk.green('Code generation completed successfully!'));
      console.log('');
      console.log(chalk.cyan('  Generated files are in: ') + chalk.yellow(outputDir));
      console.log(chalk.cyan('  Next steps:'));
      console.log(chalk.gray('  1. Check the generated files'));
      console.log(chalk.gray('  2. Run ') + chalk.cyan('pnpm lint') + chalk.gray(' to format the code'));
      console.log(chalk.gray('  3. Add the route to your menu configuration'));
      console.log('');
    } catch (error) {
      spinner.fail(chalk.red('Code generation failed'));
      throw error;
    }
  }

  /**
   * 下载 ZIP 文件
   */
  private downloadZip(url: string): Promise<Buffer> {
    return new Promise((resolve, reject) => {
      const parsedUrl = new URL(url);
      const requester = parsedUrl.protocol === 'https:' ? https : http;

      requester.get(url, (response) => {
        if (response.statusCode && (response.statusCode < 200 || response.statusCode >= 300)) {
          reject(new Error(`Server responded with ${response.statusCode}`));
          return;
        }

        const chunks: Buffer[] = [];
        response.on('data', (chunk) => chunks.push(chunk));
        response.on('end', () => resolve(Buffer.concat(chunks)));
        response.on('error', reject);
      }).on('error', reject);
    });
  }

  /**
   * 解压 ZIP 到输出目录
   */
  private async extractZip(buffer: Buffer, outputDir: string): Promise<void> {
    // 使用 unzipper 解压
    return new Promise((resolve, reject) => {
      const packageStream = require('stream').Readable.from(buffer);
      packageStream
        .pipe(unzipper.Extract({ path: outputDir }))
        .on('close', () => resolve())
        .on('error', reject);
    });
  }
}
