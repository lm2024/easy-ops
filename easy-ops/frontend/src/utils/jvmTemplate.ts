/**
 * JVM 启动模板生成（Java 8 + G1）
 * 根据节点硬件与环境（dev/test/prod）计算推荐值与可调范围，供「一键填入」使用。
 */

export type EnvType = 'dev' | 'test' | 'prod'

export interface NodeHardware {
  cpuCores: number
  totalMemoryMB: number
}

/** 单个 JVM 参数的说明（推荐值 + 范围 + 用途） */
export interface JvmParamSpec {
  flag: string
  recommended: string
  range: string
  purpose: string
  reason: string
}

/** 环境差异说明 */
export interface EnvProfile {
  key: EnvType
  label: string
  icon: string
  summary: string
  goals: string[]
  heapStrategy: string
  gcStrategy: string
  whenToTune: string[]
}

/** 一键填入完整结果 */
export interface JvmTemplatePlan {
  env: EnvType
  hardware: NodeHardware
  heapXmsMB: number
  heapXmxMB: number
  osReserveMB: number
  jvmOpts: string
  startScript: string
  stopScript: string
  params: JvmParamSpec[]
  envProfile: EnvProfile
  memoryGuide: string
  cpuGuide: string
}

const ENV_PROFILES: Record<EnvType, EnvProfile> = {
  dev: {
    key: 'dev',
    label: '开发环境',
    icon: '🌱',
    summary: '单机/共享资源，优先快速启动与省内存，容忍较长 GC 停顿，不开启诊断落盘。',
    goals: [
      '启动快、占用少，方便本地或多应用共存',
      '不要求与生产完全一致，以能跑通功能为主',
      '故障时可直接重启，无需保留 HeapDump'
    ],
    heapStrategy: '堆约为「物理内存 − 系统预留」的 50%，Xms 为 Xmx 的一半，减少启动时一次性占满内存。',
    gcStrategy: 'G1 + MaxGCPauseMillis=200ms，不写 GC 日志，降低磁盘与 I/O 开销。',
    whenToTune: [
      '本机还跑数据库/Docker 时，应下调 Xmx 或换更大内存的参考节点',
      '应用明显 OOM 时先查泄漏，再按说明中的范围上调 Xmx'
    ]
  },
  test: {
    key: 'test',
    label: '测试环境',
    icon: '🧪',
    summary: '接近生产的容量与稳定性，Xms=Xmx 避免运行期扩缩堆，开启 OOM HeapDump 便于压测/联调排障。',
    goals: [
      '验证发布包在接近生产的堆大小下是否稳定',
      '压测、集成测试时行为可复现（固定堆）',
      'OOM 时保留 dump 供开发分析'
    ],
    heapStrategy: '堆约为「物理内存 − 系统预留」的 65%，Xms=Xmx，与生产一致的固定堆策略。',
    gcStrategy: 'G1 + MaxGCPauseMillis=200ms；OOM 时写 ./logs/heapdump.hprof。',
    whenToTune: [
      '压测节点内存应 ≥ 生产单实例，否则结论不可靠',
      '多实例同机部署时，按实例数等分可用内存后再生成'
    ]
  },
  prod: {
    key: 'prod',
    label: '生产环境',
    icon: '🚀',
    summary: '稳定优先：固定堆、积极 GC 停顿目标、OOM 快速失败并留 dump，参数尽量少且每条都有理由。',
    goals: [
      '避免运行期堆伸缩带来的延迟抖动',
      '控制 Full GC / 停顿在可接受范围',
      'OOM 时进程退出并留 dump，由编排/监控拉起新实例'
    ],
    heapStrategy: '堆约为「物理内存 − 系统预留」的 70%，Xms=Xmx；单机多实例时务必手动等分后再填。',
    gcStrategy: 'G1 + MaxGCPauseMillis=100ms；仅保留 ExitOnOutOfMemoryError 与 HeapDump，不写 GC 日志（可用外部 APM/监控替代）。',
    whenToTune: [
      '8C16G 以上可维持默认；4C8G 以下建议下调 Xmx 并核对监控',
      '超高 QPS 或超大堆（>16G）请联系架构师评估 G1 Region 与并行线程'
    ]
  }
}

/** 将 MB 向下取整到合理档位（避免 1537m 这类难读数值） */
export function roundHeapMB(mb: number): number {
  if (mb <= 0) return 256
  if (mb < 1024) return Math.max(256, Math.floor(mb / 128) * 128)
  if (mb < 4096) return Math.floor(mb / 256) * 256
  return Math.floor(mb / 512) * 512
}

interface HeapCalcResult {
  xmsMB: number
  xmxMB: number
  osReserveMB: number
}

/**
 * 计算堆大小
 * 思路：先为 OS/Agent/堆外内存预留，再按环境比例分配，最后 Xms/Xmx 策略区分 dev 与 test/prod
 */
export function calculateHeap(env: EnvType, totalMemMB: number): HeapCalcResult {
  const reserveCfg: Record<EnvType, { ratio: number; minMB: number }> = {
    dev: { ratio: 0.40, minMB: 1536 },
    test: { ratio: 0.30, minMB: 1024 },
    prod: { ratio: 0.25, minMB: 2048 }
  }
  const heapRatio: Record<EnvType, number> = {
    dev: 0.50,
    test: 0.65,
    prod: 0.70
  }
  const heapCap: Record<EnvType, number> = {
    dev: 2048,
    test: 8192,
    prod: 32768
  }
  const minHeap: Record<EnvType, number> = {
    dev: 512,
    test: 1024,
    prod: 1024
  }

  const cfg = reserveCfg[env]
  const osReserveMB = Math.min(
    totalMemMB - minHeap[env],
    Math.max(cfg.minMB, Math.round(totalMemMB * cfg.ratio))
  )

  let budgetMB = totalMemMB - osReserveMB
  let xmxMB = roundHeapMB(Math.min(budgetMB * heapRatio[env], heapCap[env]))
  xmxMB = Math.max(xmxMB, minHeap[env])
  xmxMB = Math.min(xmxMB, totalMemMB - Math.min(512, Math.round(totalMemMB * 0.15)))

  let xmsMB: number
  if (env === 'dev') {
    xmsMB = roundHeapMB(Math.max(256, xmxMB * 0.5))
    xmsMB = Math.min(xmsMB, xmxMB)
  } else {
    xmsMB = xmxMB
  }

  return { xmsMB, xmxMB, osReserveMB }
}

function gcPauseMillis(env: EnvType): number {
  return env === 'prod' ? 100 : 200
}

/** 构建精简 JVM 参数字符串（Java 8 兼容） */
export function buildJvmOpts(env: EnvType, xmsMB: number, xmxMB: number): string {
  const parts = [
    `-Xms${xmsMB}m`,
    `-Xmx${xmxMB}m`,
    '-XX:+UseG1GC',
    `-XX:MaxGCPauseMillis=${gcPauseMillis(env)}`,
    '-XX:+ExitOnOutOfMemoryError',
    '-Dfile.encoding=UTF-8'
  ]
  if (env !== 'dev') {
    parts.push('-XX:+HeapDumpOnOutOfMemoryError', '-XX:HeapDumpPath=./logs/heapdump.hprof')
  }
  return parts.join(' ')
}

export function generateStartScript(jarName: string, jvmOpts: string): string {
  const jar = jarName || 'app.jar'
  return `#!/bin/bash
# EasyOps 自动生成 — 在部署目录执行
JAR_NAME=${jar}
cd "$(dirname "$0")"

# 先停掉同名 jar 的旧进程（防止端口冲突）
OLD_PIDS=$(ps -ef | grep "[j]ava.*-jar.*$JAR_NAME" | awk '{print $2}')
if [ -n "$OLD_PIDS" ]; then
  echo "发现旧进程，先停止: $OLD_PIDS"
  for p in $OLD_PIDS; do kill "$p" 2>/dev/null; done
  sleep 2
  for p in $OLD_PIDS; do kill -9 "$p" 2>/dev/null; done
fi

mkdir -p logs
nohup java ${jvmOpts} -jar "$JAR_NAME" >> logs/startup.log 2>&1 &
echo "Started PID=$! jar=$JAR_NAME"`
}

export function generateStopScript(jarName?: string): string {
  const jar = jarName || 'app.jar'
  return `#!/bin/bash
# EasyOps 自动生成 — 按 jar 包名查找并停止进程
JAR_NAME=${jar}

# 按 jar 名查找进程（排除 grep 自身）
PIDS=$(ps -ef | grep "[j]ava.*-jar.*$JAR_NAME" | awk '{print $2}')

if [ -z "$PIDS" ]; then
  echo "未找到 $JAR_NAME 的运行进程"
  exit 0
fi

echo "停止 $JAR_NAME 进程: $PIDS"
for p in $PIDS; do
  kill "$p" 2>/dev/null
done

# 等待优雅退出
sleep 3

# 检查是否还在，强杀残留
for p in $PIDS; do
  if kill -0 "$p" 2>/dev/null; then
    echo "强杀残留进程: $p"
    kill -9 "$p" 2>/dev/null
  fi
done

echo "✅ 已停止 $JAR_NAME"`
}

function heapRange(env: EnvType, xmxMB: number, totalMemMB: number): string {
  const min = env === 'dev' ? 256 : 512
  const max = Math.min(
    env === 'prod' ? 32768 : env === 'test' ? 8192 : 2048,
    totalMemMB - 512
  )
  return `${min}m ~ ${roundHeapMB(max)}m（当前推荐 ${xmxMB}m）`
}

function buildParamSpecs(
  env: EnvType,
  xmsMB: number,
  xmxMB: number,
  cpuCores: number,
  totalMemMB: number
): JvmParamSpec[] {
  const pause = gcPauseMillis(env)
  const parallelThreads = Math.max(2, Math.min(cpuCores, 8))

  const specs: JvmParamSpec[] = [
    {
      flag: '-Xms',
      recommended: `${xmsMB}m`,
      range: heapRange(env, xmsMB, totalMemMB),
      purpose: '初始堆大小',
      reason:
        env === 'dev'
          ? '开发环境半量启动，减少与其他本地服务争用内存；test/prod 应与 Xmx 相同。'
          : '与 Xmx 相同，避免运行期扩堆带来的停顿与性能波动（生产最佳实践）。'
    },
    {
      flag: '-Xmx',
      recommended: `${xmxMB}m`,
      range: heapRange(env, xmxMB, totalMemMB),
      purpose: '堆内存上限',
      reason: `按 ${totalMemMB}MB 物理内存扣除系统预留后计算；需为 OS、元空间、线程栈、直接内存留余量，不宜超过物理内存 75%。`
    },
    {
      flag: '-XX:+UseG1GC',
      recommended: '开启',
      range: 'Java 8u40+ 建议 G1；极小堆（<512m）可考虑 Serial',
      purpose: '垃圾回收器',
      reason: 'G1 适合服务端默认场景，停顿可预期，JDK8 长期运行成熟；比 CMS 更省心。'
    },
    {
      flag: '-XX:MaxGCPauseMillis',
      recommended: `${pause}`,
      range: '50 ~ 500（毫秒）',
      purpose: 'G1 停顿时间目标',
      reason:
        env === 'prod'
          ? '生产偏 100ms，在吞吐与延迟间折中；并非硬上限，过小会牺牲吞吐。'
          : '开发/测试 200ms 即可，减少 GC 调优成本。'
    },
    {
      flag: '-XX:+ExitOnOutOfMemoryError',
      recommended: '开启',
      range: '建议 test/prod 开启',
      purpose: 'OOM 时退出进程',
      reason: '便于 K8s/Systemd/监控发现异常并自动重启，避免僵尸进程继续接流量。'
    },
    {
      flag: '-Dfile.encoding',
      recommended: 'UTF-8',
      range: 'UTF-8（固定）',
      purpose: '默认字符集',
      reason: '避免日志与 HTTP 响应在 Linux 默认 ASCII/Latin1 下出现乱码。'
    }
  ]

  if (env !== 'dev') {
    specs.push({
      flag: '-XX:+HeapDumpOnOutOfMemoryError',
      recommended: '开启',
      range: 'test/prod 建议开启',
      purpose: 'OOM 时写堆转储',
      reason: '生成 ./logs/heapdump.hprof 供 MAT 分析；注意磁盘空间（约为堆大小量级）。'
    })
  }

  specs.push({
    flag: '-XX:ParallelGCThreads',
    recommended: `默认（约 ${parallelThreads}）`,
    range: `2 ~ ${Math.max(2, cpuCores)}`,
    purpose: '并行 GC 线程数',
    reason: `${cpuCores} 核机器通常无需显式设置，JVM 默认约等于核数；仅在 CPU 极强的专用机上可手动指定。`
  })

  return specs
}

export function buildMemoryGuide(totalMemMB: number, osReserveMB: number, xmxMB: number): string {
  const gb = (totalMemMB / 1024).toFixed(1)
  const reserveGb = (osReserveMB / 1024).toFixed(1)
  const heapGb = (xmxMB / 1024).toFixed(1)
  if (totalMemMB <= 4096) {
    return `${gb}GB 机器：系统预留约 ${reserveGb}GB，单应用堆推荐 ≤ ${heapGb}GB；不宜再开大堆。`
  }
  if (totalMemMB <= 8192) {
    return `${gb}GB 机器：预留 ${reserveGb}GB 给 OS/监控/Agent，堆 ${heapGb}GB 适合中小型 Spring Boot 单实例。`
  }
  if (totalMemMB <= 16384) {
    return `${gb}GB 机器：可多实例部署，每实例堆建议 ≤ ${heapGb}GB，总和不超过物理内存 70%。`
  }
  return `${gb}GB 大内存机：单实例堆 ${heapGb}GB 仍属稳健值；更大堆需评估 G1 Region 与 Full GC 风险。`
}

export function buildCpuGuide(cpuCores: number): string {
  if (cpuCores <= 2) {
    return `${cpuCores} 核：轻量应用足够；CPU 密集或高并发时优先水平扩容而非无限加大堆。`
  }
  if (cpuCores <= 8) {
    return `${cpuCores} 核：G1 默认并行线程与核数匹配，无需额外 -XX:ParallelGCThreads；关注 GC 时间与业务线程竞争。`
  }
  return `${cpuCores} 核：可考虑绑定 NUMA/隔离 GC 核；堆 >16G 时与架构师确认 G1 与 ZGC 选型。`
}

export function getEnvProfile(env: EnvType): EnvProfile {
  return ENV_PROFILES[env] ?? ENV_PROFILES.dev
}

/** 生成完整一键填入方案 */
export function buildJvmTemplatePlan(
  env: EnvType,
  hardware: NodeHardware,
  jarName?: string
): JvmTemplatePlan {
  const cpuCores = Math.max(1, hardware.cpuCores || 2)
  const totalMemMB = Math.max(1024, hardware.totalMemoryMB || 4096)
  const { xmsMB, xmxMB, osReserveMB } = calculateHeap(env, totalMemMB)
  const jvmOpts = buildJvmOpts(env, xmsMB, xmxMB)
  const jar = jarName || 'app.jar'

  return {
    env,
    hardware: { cpuCores, totalMemoryMB: totalMemMB },
    heapXmsMB: xmsMB,
    heapXmxMB: xmxMB,
    osReserveMB,
    jvmOpts,
    startScript: generateStartScript(jar, jvmOpts),
    stopScript: generateStopScript(jar),
    params: buildParamSpecs(env, xmsMB, xmxMB, cpuCores, totalMemMB),
    envProfile: getEnvProfile(env),
    memoryGuide: buildMemoryGuide(totalMemMB, osReserveMB, xmxMB),
    cpuGuide: buildCpuGuide(cpuCores)
  }
}
