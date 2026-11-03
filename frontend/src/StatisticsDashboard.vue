<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
const props = defineProps({ stats: { type: Object, required: true } })
const methodsChart = ref(null), stationsChart = ref(null), monthlyChart = ref(null)
const charts = []
const format = (value, digits = 0) => value == null ? '暂无数据' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: digits })
const summary = computed(() => props.stats.summary || {})
const methods = computed(() => ['SELL', 'GIVE', 'RECYCLE'].map(type => {
  const row = (props.stats.methods || []).find(x => x.process_type === type) || {}
  return { name: { SELL: '出售', GIVE: '赠送', RECYCLE: '回收' }[type], value: Number(row.quantity || 0), percentage: Number(row.percentage || 0) }
}))
function render() {
  for (const chart of charts.splice(0)) chart.dispose()
  const draw = (element, option) => { if (element) { const chart = echarts.init(element); chart.setOption(option); charts.push(chart) } }
  const stations = props.stats.stations || [], months = props.stats.monthly || []
  draw(methodsChart.value, {
    color: ['#ff922e', '#43ae87', '#7797d6'], tooltip: { trigger: 'item' }, legend: { bottom: 0 },
    series: [{ name: '物品数量', type: 'pie', radius: ['43%', '68%'], center: ['50%', '44%'],
      label: { formatter: '{b} {d}%' }, stillShowZeroSum: false, data: methods.value }]
  })
  draw(stationsChart.value, {
    tooltip: { trigger: 'axis' }, grid: { left: 20, right: 35, top: 15, bottom: 35, containLabel: true },
    xAxis: { type: 'value', name: 'kg' }, yAxis: { type: 'category', inverse: true, data: stations.map(x => x.name), axisLabel: { width: 130, overflow: 'truncate' } },
    dataZoom: stations.length > 6 ? [{ type: 'slider', yAxisIndex: 0, startValue: 0, endValue: 5, right: 0 }] : [],
    series: [{ name: '累计回收重量（kg）', type: 'bar', barMaxWidth: 26, itemStyle: { color: '#43ae87', borderRadius: [0, 5, 5, 0] }, data: stations.map(x => Number(x.total_weight)) }]
  })
  draw(monthlyChart.value, {
    color: ['#ff922e', '#43ae87', '#7797d6'], tooltip: { trigger: 'axis' }, legend: { top: 0 },
    grid: { left: 25, right: 28, top: 60, bottom: 30, containLabel: true },
    xAxis: { type: 'category', data: months.map(x => x.month) },
    yAxis: [{ type: 'value', name: '物品数（件）', minInterval: 1 }, { type: 'value', name: '回收重量（kg）' }],
    series: [
      { name: '当月发布', type: 'bar', data: months.map(x => Number(x.published_items)) },
      { name: '当月完成', type: 'bar', data: months.map(x => Number(x.completed_items)) },
      { name: '当月回收重量', type: 'line', yAxisIndex: 1, data: months.map(x => Number(x.recycle_weight)) }
    ]
  })
}
let observer
onMounted(() => {
  render()
  observer = new ResizeObserver(() => charts.forEach(chart => chart.resize()))
  for (const element of [methodsChart.value, stationsChart.value, monthlyChart.value]) if (element) observer.observe(element)
})
watch(() => props.stats, async () => { await nextTick(); render() })
onBeforeUnmount(() => { observer?.disconnect(); charts.forEach(chart => chart.dispose()) })
</script>

<template>
  <section class="stats-dashboard">
    <div class="section-title"><div><h2>校园循环成果</h2><span>累计成果覆盖全部历史数据，月度趋势展示近12个月</span></div></div>
    <div class="stat-metrics">
      <el-card><span>累计回收重量</span><strong>{{format(summary.total_recycle_weight, 2)}} <small>kg</small></strong><p>已完成回收记录的实际重量合计</p></el-card>
      <el-card><span>已完成回收次数</span><strong>{{format(summary.completed_recycles)}} <small>次</small></strong><p>已完成订单对应的回收记录数</p></el-card>
      <el-card><span>物品完成率</span><strong>{{format(summary.completion_rate, 2)}} <small>%</small></strong><p>已完成 {{format(summary.completed_items)}} / 累计发布 {{format(summary.total_items)}} 件</p></el-card>
      <el-card><span>平均处理天数</span><strong>{{format(summary.avg_processing_days, 2)}} <small v-if="summary.avg_processing_days != null">天</small></strong><p>已完成物品从发布到完成的平均用时</p></el-card>
    </div>
    <div class="stat-chart-grid">
      <el-card><h3>出售／赠送／回收占比</h3><p class="stat-note">按累计发布物品计算，包含撤回及待审核记录</p><div ref="methodsChart" class="stat-chart" role="img" aria-label="出售赠送回收物品占比图"></div>
        <div class="method-summary"><span v-for="method in methods" :key="method.name">{{method.name}} {{format(method.value)}} 件 · {{format(method.percentage,2)}}%</span></div>
        <el-empty v-if="!Number(summary.total_items)" description="暂无发布数据" :image-size="55"/>
      </el-card>
      <el-card><h3>各回收点累计重量</h3><p class="stat-note">仅汇总已完成回收，含当前已关闭的回收点</p><div ref="stationsChart" class="stat-chart" role="img" aria-label="各回收点累计重量柱状图"></div>
        <el-empty v-if="!stats.stations?.length" description="暂无回收点" :image-size="55"/>
      </el-card>
    </div>
    <el-card><h3>月度趋势</h3><p class="stat-note">{{stats.trend_start}} 至 {{stats.trend_end}} · 发布按发布时间，完成按实际完成时间，重量按回收记录时间；本月数据持续累计</p><div ref="monthlyChart" class="stat-chart stat-trend" role="img" aria-label="近十二个月发布完成及回收重量趋势图"></div>
      <el-collapse><el-collapse-item title="查看月度明细与回收次数" name="months"><el-table :data="stats.monthly"><el-table-column prop="month" label="月份"/><el-table-column prop="published_items" label="发布（件）"/><el-table-column prop="completed_items" label="完成（件）"/><el-table-column prop="recycle_count" label="回收（次）"/><el-table-column prop="recycle_weight" label="回收重量（kg）"/></el-table></el-collapse-item></el-collapse>
    </el-card>
    <el-card><h3>环保贡献排行</h3><p class="stat-note">依据环保积分流水汇总，积分用于记录参与贡献</p><el-table :data="stats.ranking" empty-text="暂无环保积分记录"><el-table-column type="index" label="排名" width="80"/><el-table-column prop="real_name" label="用户"/><el-table-column prop="total_points" label="环保积分"/></el-table></el-card>
    <p class="stat-note">完成率＝全部已完成物品数 ÷ 全部发布物品数；没有发布时显示0%。平均处理天数仅统计时间有效的已完成物品，没有记录时显示“暂无数据”。</p>
  </section>
</template>

<style>
.stat-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.stat-metrics .el-card__body{padding:24px 20px}.stat-metrics span{color:#747b83;font-size:14px}.stat-metrics strong{display:block;margin-top:16px;font-size:30px;color:var(--orange);overflow-wrap:anywhere}.stat-metrics small{font-size:15px;font-weight:400}.stat-metrics p,.stat-note{font-size:12px;color:#858b93;line-height:1.8}.stat-metrics p{margin:12px 0 0}.stat-chart-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}.stats-dashboard h2{margin:0 0 8px}.stats-dashboard h3{margin:0 0 8px;font-size:17px}.stat-chart{width:100%;height:300px}.stat-trend{height:350px}.method-summary{display:flex;gap:14px;flex-wrap:wrap;color:#686f77;font-size:12px}@media(max-width:1000px){.stat-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:700px){.stat-chart-grid{grid-template-columns:1fr}.stat-metrics{gap:10px}.stat-metrics strong{font-size:25px}.stat-metrics .el-card__body{padding:18px 12px}}
</style>
