import {dirname, relative, resolve} from 'path'
import type {ConfigEnv, UserConfig} from 'vite'
import {loadEnv, normalizePath} from 'vite'
import {createVitePlugins} from './build/vite'
import {exclude, include} from "./build/vite/optimize"
// 当前执行node命令时文件夹的地址(工作目录)
const root = process.cwd()

// 路径查找
function pathResolve(dir: string) {
    return resolve(root, '.', dir)
}

function getRelativeScssUsePath(filename: string, targetPath: string) {
    const cleanFilename = filename.split('?')[0]
    const relativePath = normalizePath(relative(dirname(cleanFilename), targetPath))
    return relativePath.startsWith('.') ? relativePath : `./${relativePath}`
}

// https://vitejs.dev/config/
export default ({command, mode}: ConfigEnv): UserConfig => {
    let env = {} as any
    const isBuild = command === 'build'
    // 统一从 Vite 的 mode 解析环境变量：`pnpm dev` = `vite --mode env.local`，会加载 `.env.local`。
    // 避免解析 process.argv，`--mode=env.local`（带等号）等写法也能正确取到 mode。
    env = loadEnv(mode, root)
    const variablesScssPath = pathResolve('src/styles/variables.scss')
    return {
        base: env.VITE_BASE_PATH,
        root: root,
        // 服务端渲染
        server: {
            port: env.VITE_PORT ? Number(env.VITE_PORT) : 3000, // 端口号，本地默认统一为 3000
            host: "0.0.0.0",
            open: env.VITE_OPEN === 'true',
            // 本地跨域代理. 目前注释的原因：暂时没有用途，server 端已经支持跨域
            // proxy: {
            //   ['/admin-api']: {
            //     target: env.VITE_BASE_URL,
            //     ws: false,
            //     changeOrigin: true,
            //     rewrite: (path) => path.replace(new RegExp(`^/admin-api`), ''),
            //   },
            // },
        },
        // 项目使用的vite插件。 单独提取到build/vite/plugin中管理
        plugins: createVitePlugins(isBuild, env),
        css: {
            lightningcss: {
                // Preserve legacy star-hack declarations by stripping invalid syntax during minification.
                errorRecovery: true
            },
            preprocessorOptions: {
                scss: {
                    additionalData: (source: string, filename: string) => {
                        const normalizedFilename = normalizePath(filename)
                        // Windows 下更容易触发重复注入：定义或显式转导变量的文件，不能再次注入同一个
                        // `@use ... as *`，否则 Sass 会报 duplicate global variables。
                        if (
                            normalizedFilename.endsWith('/src/styles/variables.scss') ||
                            normalizedFilename.endsWith('/src/styles/global.module.scss')
                        ) {
                            return source
                        }
                        return `@use "${getRelativeScssUsePath(filename, variablesScssPath)}" as *;\n${source}`
                    },
                    api: 'modern-compiler'
                }
            }
        },
        resolve: {
            extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.scss', '.css'],
            alias: [
                {
                    find: /\@\//,
                    replacement: `${pathResolve('src')}/`
                }
            ]
        },
        build: {
            chunkSizeWarningLimit: 2000,
            minify: 'oxc',
            outDir: env.VITE_OUT_DIR || 'dist',
            reportCompressedSize: false,
            sourcemap: env.VITE_SOURCEMAP === 'true' ? 'inline' : false,
            rollupOptions: {
                output: {
                    minify: {
                        compress: {
                            dropDebugger: env.VITE_DROP_DEBUGGER === 'true',
                            dropConsole: env.VITE_DROP_CONSOLE === 'true'
                        }
                    },
                    codeSplitting: {
                        groups: [
                            { name: 'echarts', test: /node_modules[\\/]echarts[\\/]/ }, // 将 echarts 单独打包，参考 https://gitee.com/yudaocode/yudao-ui-admin-vue3/issues/IAB1SX 讨论
                            { name: 'form-create', test: /node_modules[\\/]@form-create[\\/]element-ui[\\/]/ }, // 参考 https://github.com/yudaocode/yudao-ui-admin-vue3/issues/148 讨论
                            { name: 'form-designer', test: /node_modules[\\/]@form-create[\\/]designer[\\/]/ }
                        ]
                    }
                },
            },
        },
        optimizeDeps: {include, exclude}
    }
}
