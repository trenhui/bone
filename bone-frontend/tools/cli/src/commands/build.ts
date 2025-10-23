import { Command } from 'commander';
import { build, mergeConfig } from 'vite';
import { microFePreset } from '../../build/src/micro-fe-preset';
import { resolve, relative } from 'path';
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'fs';
import chalk from 'chalk';
import ora from 'ora';
import { execSync } from 'child_process';

export class BuildCommand {
  private command: Command;

  constructor() {
    this.command = new Command('build');
    this.setup();
  }

  public getCommand(): Command {
    return this.command;
  }

  private setup(): void {
    this.command
      .description('Build Bone applications for production')
      .option('-a, --app <name>', 'Specify the application to build', 'main')
      .option('-s, --subapp', 'Build as a sub-application', false)
      .option('-c, --config <path>', 'Path to custom Vite config file')
      .option('--no-sandbox', 'Disable sandbox isolation', false)
      .option('--no-minify', 'Disable code minification', false)
      .option('--analyze', 'Analyze bundle size', false)
      .option('--out-dir <path>', 'Output directory', 'dist')
      .option('-m, --mode <mode>', 'Build mode', 'production')
      .action(async (options: any) => {
        try {
          await this.execute(options);
        } catch (error) {
          console.error(chalk.red('Failed to build application:'), error);
          process.exit(1);
        }
      });
  }

  private async execute(options: any): Promise<void> {
    const { 
      app, 
      subapp, 
      config: customConfigPath, 
      sandbox, 
      minify, 
      analyze, 
      outDir, 
      mode 
    } = options;
    
    const spinner = ora(`Building application: ${app}...`).start();

    try {
      // 设置构建模式环境变量
      process.env.NODE_ENV = mode;
      process.env.VITE_MODE = mode;

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
        enablePerformance: true
      });

      // 覆盖构建配置
      baseConfig.build = {
        ...baseConfig.build,
        outDir: resolve(appRoot, outDir),
        minify: minify ? 'terser' : false
      };

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

      // 如果启用分析，添加分析插件
      if (analyze) {
        let analyzerPlugin;
        try {
          const { visualizer } = await import('rollup-plugin-visualizer');
          analyzerPlugin = visualizer({
            filename: resolve(appRoot, `${outDir}/stats.html`),
            open: true,
            gzipSize: true,
            brotliSize: true
          });
          
          customConfig = {
            ...customConfig,
            plugins: [
              ...(Array.isArray(customConfig.plugins) ? customConfig.plugins : []),
              analyzerPlugin
            ]
          };
        } catch (error) {
          console.warn(chalk.yellow('rollup-plugin-visualizer not installed, skipping bundle analysis'));
        }
      }

      // 合并配置
      const viteConfig = mergeConfig(baseConfig, customConfig);

      spinner.text = 'Compiling application...';
      
      // 开始构建
      const startTime = Date.now();
      const result = await build({
        ...viteConfig,
        root: appRoot,
        mode
      });
      
      const endTime = Date.now();
      const buildTime = ((endTime - startTime) / 1000).toFixed(2);
      
      spinner.succeed(chalk.green(`Application built successfully in ${buildTime}s!`));
      
      // 生成构建信息文件
      await this.generateBuildInfo(appRoot, outDir, app, subapp, buildTime);
      
      // 输出构建信息
      const outputPath = resolve(appRoot, outDir);
      console.log('');
      console.log(chalk.bold('  Build Summary:'));
      console.log(chalk.bold('  -------------'));
      console.log(chalk.bold('  Application:'), chalk.cyan(app));
      console.log(chalk.bold('  Type:'), subapp ? chalk.yellow('Sub-application') : chalk.green('Main application'));
      console.log(chalk.bold('  Mode:'), chalk.magenta(mode));
      console.log(chalk.bold('  Output:'), chalk.blue(relative(process.cwd(), outputPath)));
      console.log(chalk.bold('  Time:'), chalk.gray(`${buildTime}s`));
      
      if (subapp) {
        console.log(chalk.bold('  Sandbox:'), sandbox ? chalk.green('Enabled') : chalk.red('Disabled'));
      }
      
      console.log(chalk.bold('  Minify:'), minify ? chalk.green('Enabled') : chalk.red('Disabled'));
      
      // 如果启用了分析，提示分析报告位置
      if (analyze) {
        const statsPath = relative(process.cwd(), resolve(appRoot, `${outDir}/stats.html`));
        console.log(chalk.bold('  Bundle Analysis:'), chalk.green(`file://${statsPath}`));
      }
      
      console.log('');
      
      // 检查构建产物大小
      await this.checkBuildSize(outputPath);

    } catch (error: any) {
      spinner.fail(chalk.red('Build failed'));
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
    return isSubApp ? `apps/${appName}` : `apps/main`;
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

  private async generateBuildInfo(appRoot: string, outDir: string, appName: string, isSubApp: boolean, buildTime: string): Promise<void> {
    const buildInfo = {
      app: appName,
      type: isSubApp ? 'subapp' : 'main',
      version: process.env.npm_package_version || '1.0.0',
      buildTime: new Date().toISOString(),
      duration: `${buildTime}s`,
      commit: this.getGitCommit(),
      branch: this.getGitBranch()
    };

    const outPath = resolve(appRoot, outDir, 'build-info.json');
    mkdirSync(resolve(appRoot, outDir), { recursive: true });
    writeFileSync(outPath, JSON.stringify(buildInfo, null, 2));
  }

  private getGitCommit(): string {
    try {
      return execSync('git rev-parse --short HEAD', { encoding: 'utf-8' }).trim();
    } catch {
      return 'unknown';
    }
  }

  private getGitBranch(): string {
    try {
      return execSync('git rev-parse --abbrev-ref HEAD', { encoding: 'utf-8' }).trim();
    } catch {
      return 'unknown';
    }
  }

  private async checkBuildSize(outputPath: string): Promise<void> {
    // 简单的构建大小检查
    const files = [
      'assets/index.js',
      'assets/index.css',
      'index.js', // 子应用可能使用这个命名
      'main.js' // 可能的其他命名
    ];

    let hasLargeFiles = false;
    
    for (const file of files) {
      const filePath = resolve(outputPath, file);
      if (existsSync(filePath)) {
        try {
          const stats = await import('fs').then(fs => fs.promises.stat(filePath));
          const sizeInKB = (stats.size / 1024).toFixed(2);
          const sizeInMB = (stats.size / (1024 * 1024)).toFixed(2);
          
          const sizeStr = stats.size > 1024 * 1024 
            ? `${sizeInMB} MB` 
            : `${sizeInKB} KB`;
          
          // 检查是否过大 (> 1MB 警告)
          if (stats.size > 1024 * 1024) {
            console.log(chalk.yellow(`  ⚠️  ${file}: ${sizeStr} (large bundle)`));
            hasLargeFiles = true;
          } else {
            console.log(chalk.gray(`  ${file}: ${sizeStr}`));
          }
        } catch (error) {
          // 忽略错误
        }
      }
    }
    
    if (hasLargeFiles) {
      console.log('');
      console.log(chalk.yellow('  Tips:'));
      console.log(chalk.yellow('  - Consider code splitting to reduce bundle size'));
      console.log(chalk.yellow('  - Check for unused dependencies'));
      console.log(chalk.yellow('  - Use dynamic imports for route-based code splitting'));
      console.log('');
    }
  }
}