import { Command, Option } from 'commander';
import { build, createServer, mergeConfig } from 'vite';
import { microFePreset } from '../../build/src/micro-fe-preset';
import { resolve, relative } from 'path';
import { existsSync, readFileSync } from 'fs';
import chalk from 'chalk';
import ora from 'ora';

export class DevCommand {
  private command: Command;

  constructor() {
    this.command = new Command('dev');
    this.setup();
  }

  public getCommand(): Command {
    return this.command;
  }

  private setup(): void {
    this.command
      .description('Start development server for Bone applications')
      .option('-a, --app <name>', 'Specify the application to run', 'main')
      .option('-p, --port <number>', 'Port to run the server on', '3000')
      .option('-s, --subapp', 'Run as a sub-application', false)
      .option('-c, --config <path>', 'Path to custom Vite config file')
      .option('--no-sandbox', 'Disable sandbox isolation', false)
      .option('--no-hmr', 'Disable hot module replacement', false)
      .option('--no-open', 'Do not open the browser automatically', false)
      .action(async (options: any) => {
        try {
          await this.execute(options);
        } catch (error) {
          console.error(chalk.red('Failed to start development server:'), error);
          process.exit(1);
        }
      });
  }

  private async execute(options: any): Promise<void> {
    const { app, port, subapp, config: customConfigPath, sandbox, hmr, open } = options;
    
    const spinner = ora('Starting development server...').start();

    try {
      // 确定应用路径
      const appPath = this.resolveAppPath(app, subapp);
      const appRoot = resolve(process.cwd(), appPath);
      
      // 检查应用是否存在
      if (!existsSync(appRoot)) {
        throw new Error(`Application not found: ${app}`);
      }

      // 加载应用配置
      const appConfig = await this.loadAppConfig(appRoot, app, subapp);
      
      // 创建基本配置
      const baseConfig = microFePreset({
        appName: app,
        isSubApp: subapp,
        enableSandbox: sandbox,
        enableHMR: hmr
      });

      // 加载自定义配置
      let customConfig = {};
      if (customConfigPath) {
        const customConfigFullPath = resolve(process.cwd(), customConfigPath);
        if (existsSync(customConfigFullPath)) {
          const configModule = await import(customConfigFullPath);
          customConfig = configModule.default || configModule;
        } else {
          console.warn(chalk.yellow(`Custom config file not found: ${customConfigPath}`));
        }
      } else {
        // 尝试加载应用根目录下的vite.config.ts
        const defaultConfigPath = resolve(appRoot, 'vite.config.ts');
        if (existsSync(defaultConfigPath)) {
          const configModule = await import(defaultConfigPath);
          customConfig = configModule.default || configModule;
        }
      }

      // 合并配置
      const viteConfig = mergeConfig(baseConfig, customConfig);
      
      // 覆盖端口配置
      viteConfig.server = {
        ...viteConfig.server,
        port: parseInt(port, 10),
        open
      };

      spinner.text = 'Creating development server...';
      
      // 创建开发服务器
      const server = await createServer({
        ...viteConfig,
        root: appRoot
      });

      // 启动服务器
      await server.listen();

      spinner.succeed(chalk.green('Development server started successfully!'));
      
      // 输出服务器信息
      const serverInfo = server.config.server;
      const protocol = server.config.server?.https ? 'https' : 'http';
      const host = serverInfo?.host === true ? 'localhost' : serverInfo?.host || 'localhost';
      const serverPort = serverInfo?.port || 3000;
      
      const url = `${protocol}://${host}:${serverPort}`;
      
      console.log('');
      console.log(chalk.bold('  Application:'), chalk.cyan(app));
      console.log(chalk.bold('  Type:'), subapp ? chalk.yellow('Sub-application') : chalk.green('Main application'));
      console.log(chalk.bold('  Mode:'), chalk.magenta('Development'));
      console.log(chalk.bold('  URL:'), chalk.blue(url));
      console.log(chalk.bold('  App Path:'), chalk.gray(relative(process.cwd(), appRoot)));
      
      if (subapp) {
        console.log(chalk.bold('  Sandbox:'), sandbox ? chalk.green('Enabled') : chalk.red('Disabled'));
      }
      
      console.log('');
      console.log(chalk.gray('  Press Ctrl+C to stop the server'));
      console.log('');
      
      // 监听服务器错误
      server.httpServer?.on('error', (error) => {
        console.error(chalk.red('Server error:'), error);
      });

    } catch (error: any) {
      spinner.fail(chalk.red('Failed to start development server'));
      throw error;
    }
  }

  private resolveAppPath(appName: string, isSubApp: boolean): string {
    const possiblePaths = [
      // 检查apps目录下的应用
      `apps/${appName}`,
      // 检查packages目录下的应用
      `packages/${appName}`,
      // 当前目录
      '.'
    ];

    for (const path of possiblePaths) {
      const fullPath = resolve(process.cwd(), path);
      if (existsSync(fullPath)) {
        // 验证是否是有效的应用目录
        if (existsSync(resolve(fullPath, 'src/index.ts')) || 
            existsSync(resolve(fullPath, 'src/main.tsx')) ||
            existsSync(resolve(fullPath, 'index.html'))) {
          return path;
        }
      }
    }

    // 如果找不到，返回默认路径
    return isSubApp ? `apps/${appName}` : `apps/bone-shell`;
  }

  private async loadAppConfig(appRoot: string, appName: string, isSubApp: boolean): Promise<any> {
    // 尝试加载package.json获取应用信息
    const packageJsonPath = resolve(appRoot, 'package.json');
    if (existsSync(packageJsonPath)) {
      try {
        const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf-8'));
        
        // 检查是否有bone配置
        if (packageJson.bone) {
          return packageJson.bone;
        }
      } catch (error) {
        console.warn(chalk.yellow('Failed to parse package.json'));
      }
    }

    // 返回默认配置
    return {
      name: appName,
      isSubApp,
      version: '1.0.0'
    };
  }
}