#!/usr/bin/env node

import { Command } from 'commander';
// import { DevCommand } from './commands/dev';
// import { BuildCommand } from './commands/build';
import { GenerateCommand } from './commands/generate/GenerateCommand';
import chalk from 'chalk';
import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

// 版本常量
export const BONE_CLI_VERSION = '1.0.0';

// 创建主命令实例
const program = new Command();

// 设置CLI基本信息
program
  .name('bone')
  .description('Bone Frontend Development Framework CLI')
  .version(BONE_CLI_VERSION, '-v, --version', 'Display version number');

// 加载并注册命令
function registerCommands(): void {
  // // 开发服务器命令
  // const devCommand = new DevCommand();
  // program.addCommand(devCommand.getCommand());
  //
  // // 构建命令
  // const buildCommand = new BuildCommand();
  // program.addCommand(buildCommand.getCommand());

  // 代码生成命令
  const generateCommand = new GenerateCommand();
  program.addCommand(generateCommand.getCommand());

  // 新增应用命令
  program
    .command('create')
    .description('Create a new Bone application')
    .argument('<name>', 'Application name')
    .option('-t, --template <template>', 'Template to use', 'react')
    .option('-s, --subapp', 'Create as sub-application', false)
    .option('-d, --directory <dir>', 'Directory to create the app in', 'apps')
    .action((name: string, options: any) => {
      console.log(chalk.green(`Creating new Bone application: ${name}`));
      console.log(chalk.gray(`Template: ${options.template}`));
      console.log(chalk.gray(`Type: ${options.subapp ? 'Sub-application' : 'Main application'}`));
      console.log(chalk.gray(`Directory: ${options.directory}`));
      // 实际创建逻辑将在后续实现
      console.log(chalk.yellow('Note: Application creation functionality coming soon!'));
    });
  
  // 列出应用命令
  program
    .command('list')
    .description('List all Bone applications')
    .action(() => {
      const appsDir = resolve(process.cwd(), 'apps');
      if (existsSync(appsDir)) {
        const fs = require('fs');
        const path = require('path');
        
        try {
          const apps = fs.readdirSync(appsDir)
            .filter((file: string) => fs.statSync(path.join(appsDir, file)).isDirectory())
            .map((dir: string) => {
              const packageJsonPath = path.join(appsDir, dir, 'package.json');
              let isSubApp = false;
              let version = 'unknown';
              
              if (fs.existsSync(packageJsonPath)) {
                try {
                  const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf-8'));
                  isSubApp = packageJson.bone?.isSubApp || false;
                  version = packageJson.version || 'unknown';
                } catch (error) {
                  // 忽略错误
                }
              }
              
              return {
                name: dir,
                type: isSubApp ? 'Sub-application' : 'Main application',
                version
              };
            });
          
          if (apps.length === 0) {
            console.log(chalk.yellow('No applications found in apps directory'));
          } else {
            console.log(chalk.bold('Available Applications:'));
            console.log(chalk.gray('----------------------'));
            
            apps.forEach((app: any) => {
              console.log(chalk.cyan(`  • ${app.name}`));
              console.log(chalk.gray(`    Type: ${app.type}`));
              console.log(chalk.gray(`    Version: ${app.version}`));
              console.log('');
            });
          }
        } catch (error) {
          console.error(chalk.red('Failed to list applications:'), error);
        }
      } else {
        console.log(chalk.yellow('No apps directory found'));
      }
    });
  
  // 清理命令
  program
    .command('clean')
    .description('Clean build artifacts')
    .option('-a, --all', 'Clean all build artifacts including node_modules', false)
    .action((options: any) => {
      console.log(chalk.yellow('Cleaning build artifacts...'));
      // 实际清理逻辑将在后续实现
      console.log(chalk.green('Clean completed!'));
    });
}

// 显示欢迎信息
function showWelcomeMessage(): void {
  const welcomeMessage = `
${chalk.cyan.bold('  ___  ___')}
${chalk.cyan.bold(' / _ \/ _ \\')} ${chalk.gray('Bone Frontend Framework')}
${chalk.cyan.bold('| (_) | (_) |')} ${chalk.gray(`v${BONE_CLI_VERSION}`)}
${chalk.cyan.bold(' \___/\___/')} ${chalk.gray('Enterprise Ready')}
  `;
  
  console.log(welcomeMessage);
}

// 检查更新
async function checkForUpdates(): Promise<void> {
  try {
    // 这里可以实现检查更新的逻辑
    // 暂时跳过实际检查
    // console.log(chalk.gray('Checking for updates...'));
  } catch (error) {
    // 忽略更新检查错误
  }
}

// 配置帮助信息
function configureHelp(): void {
  program.addHelpText('after', `
${chalk.gray('Examples:')}
  ${chalk.cyan('bone list')}        ${chalk.gray('# List all applications')}
  ${chalk.cyan('bone generate')}    ${chalk.gray('# Generate frontend CRUD code from backend codegen service')}
  ${chalk.cyan('bone generate -t 1,2 -o ./src/pages')}    ${chalk.gray('# Generate tables 1 and 2 to src/pages')}
  `);
}

// 主函数
async function main(): Promise<void> {
  try {
    // 配置帮助信息
    configureHelp();
    
    // 注册命令
    registerCommands();
    
    // 显示欢迎信息
    if (process.argv.length <= 2) {
      showWelcomeMessage();
      await checkForUpdates();
    }
    
    // 解析命令行参数
    await program.parseAsync(process.argv);
    
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
    program.help();
    process.exit(1);
  }
}

// 执行主函数
main().catch((error: unknown) => {
  console.error(chalk.red('Fatal error:'), error);
  process.exit(1);
});