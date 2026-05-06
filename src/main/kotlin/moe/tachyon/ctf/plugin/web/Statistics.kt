package moe.tachyon.ctf.plugin.web

import org.intellij.lang.annotations.Language

@Language("HTML")
const val statisticsHtml = $$$$"""
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>游戏对局数据统计看板</title>
    <script src="https://cdn.staticfile.net/echarts/5.5.0/echarts.min.js"></script>
    <style>
        :root {
            --bg-color: #f4f7f9;
            --card-bg: #ffffff;
            --primary-color: #1890ff;
            --primary-hover: #40a9ff;
            --text-main: #333333;
            --text-sub: #666666;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-main);
            margin: 0;
            padding: 20px;
        }
        .header {
            text-align: center;
            margin-bottom: 30px;
        }
        .header h1 {
            margin-bottom: 20px;
            color: #2c3e50;
        }
        
        /* 控制栏布局：下拉框和按钮放一排 */
        .controls-container {
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 15px;
            flex-wrap: wrap;
        }
        
        .select-container {
            position: relative;
            display: inline-block;
        }
        select {
            appearance: none;
            padding: 10px 40px 10px 20px;
            font-size: 16px;
            border: 1px solid #d9d9d9;
            border-radius: 6px;
            background-color: var(--card-bg);
            cursor: pointer;
            outline: none;
            box-shadow: 0 2px 4px rgba(0,0,0,0.02);
            transition: border-color 0.3s;
            min-width: 220px;
        }
        select:hover { border-color: var(--primary-color); }
        .select-container::after {
            content: "▼";
            font-size: 12px;
            color: var(--text-sub);
            position: absolute;
            right: 15px;
            top: 50%;
            transform: translateY(-50%);
            pointer-events: none;
        }
        
        /* 刷新按钮样式 */
        .btn-refresh {
            padding: 10px 20px;
            font-size: 16px;
            background-color: var(--primary-color);
            color: #fff;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
            transition: background-color 0.3s, transform 0.1s;
        }
        .btn-refresh:hover {
            background-color: var(--primary-hover);
        }
        .btn-refresh:active {
            transform: scale(0.98);
        }
        
        .dashboard {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(450px, 1fr));
            gap: 20px;
            max-width: 1400px;
            margin: 0 auto;
        }
        .chart-card {
            background-color: var(--card-bg);
            border-radius: 10px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.05);
            padding: 20px;
            height: 350px;
            transition: box-shadow 0.3s;
        }
        .chart-card:hover {
            box-shadow: 0 6px 16px rgba(0,0,0,0.1);
        }
        .chart-container {
            width: 100%;
            height: 100%;
        }
        
        #loading {
            text-align: center;
            font-size: 18px;
            color: var(--text-sub);
            display: none;
            margin-top: 50px;
        }

        @media (max-width: 600px) {
            .dashboard { grid-template-columns: 1fr; }
            .chart-card { height: 300px; }
        }
    </style>
</head>
<body>

    <div class="header">
        <h1>游戏对局数据统计看板</h1>
        <div class="controls-container">
            <div class="select-container">
                <select id="nameSelect" onchange="handleSelectChange(this.value)">
                    <option value="">加载列表中...</option>
                </select>
            </div>
            <button class="btn-refresh" onclick="refreshCurrentData()">刷新数据</button>
        </div>
    </div>

    <div id="loading">正在加载数据，请稍候...</div>

    <div class="dashboard" id="dashboard" style="display: none;">
        <!-- 第一组：绝对数值统计 -->
        <div class="chart-card"><div id="chart-withNone" class="chart-container"></div></div>
        <div class="chart-card"><div id="chart-withBot" class="chart-container"></div></div> <!-- 新增: 和BOT对局 -->
        <div class="chart-card"><div id="chart-withOther" class="chart-container"></div></div>
        <div class="chart-card"><div id="chart-catch" class="chart-container"></div></div>
        <div class="chart-card"><div id="chart-flag" class="chart-container"></div></div>
        <div class="chart-card"><div id="chart-win" class="chart-container"></div></div>
        <div class="chart-card"><div id="chart-totalTime" class="chart-container"></div></div>
        
        <!-- 第二组：场均/条件统计 (要求15场以上) -->
        <div class="chart-card"><div id="chart-avgWinRate" class="chart-container"></div></div>
        <div class="chart-card"><div id="chart-avgCatch" class="chart-container"></div></div>
        <div class="chart-card"><div id="chart-avgFlag" class="chart-container"></div></div>
        <div class="chart-card"><div id="chart-avgShortestTime" class="chart-container"></div></div>
    </div>

    <script>
        let chartInstances = [];
        const TOP_N = 5; 

        document.addEventListener('DOMContentLoaded', async () => {
            await loadNameList();
        });

        // 将获取列表独立成一个函数
        async function loadNameList() {
            try {
                const response = await fetch('/list');
                const list = await response.json();
                
                const select = document.getElementById('nameSelect');
                // 记住当前选中的值，刷新列表后恢复
                const currentValue = select.value;
                
                select.innerHTML = '<option value="">-- 请选择一个配置名称 --</option>';
                
                list.forEach(name => {
                    const option = document.createElement('option');
                    option.value = name;
                    option.textContent = name;
                    select.appendChild(option);
                });

                if (currentValue && list.includes(currentValue)) {
                    select.value = currentValue;
                }
            } catch (error) {
                console.error("加载列表失败:", error);
                const select = document.getElementById('nameSelect');
                if(select.options.length <= 1) {
                    select.innerHTML = '<option value="">加载失败，请检查后端</option>';
                }
            }
        }

        window.addEventListener('resize', () => {
            chartInstances.forEach(chart => chart.resize());
        });

        // 刷新按钮调用的方法
        async function refreshCurrentData() {
            const select = document.getElementById('nameSelect');
            const currentName = select.value;
            
            if (!currentName) {
                alert("请先选择一个配置名称后再刷新！");
                return;
            }
            
            // 刷新时可选：同时刷新一下名称列表
            await loadNameList();
            
            // 重新请求统计数据
            handleSelectChange(currentName);
        }

        async function handleSelectChange(name) {
            if (!name) {
                document.getElementById('dashboard').style.display = 'none';
                return;
            }
            
            document.getElementById('dashboard').style.display = 'none';
            document.getElementById('loading').style.display = 'block';

            try {
                const response = await fetch(`/statistics_data?name=${encodeURIComponent(name)}`);
                const data = await response.json();
                renderAllCharts(data);
                
                document.getElementById('loading').style.display = 'none';
                document.getElementById('dashboard').style.display = 'grid';
                setTimeout(() => {
                    chartInstances.forEach(chart => chart.resize());
                }, 50);
                
            } catch (error) {
                console.error("加载统计数据失败:", error);
                alert("加载数据失败，请查看控制台。");
                document.getElementById('loading').style.display = 'none';
            }
        }

        function getTopN(mapData, scale = 1) {
            if (!mapData) return [];
            return Object.entries(mapData)
                .map(([key, val]) => ({ name: `${key}组`, value: val * scale }))
                .sort((a, b) => b.value - a.value)
                .slice(0, TOP_N)
                .reverse(); 
        }

        function getTopNAvg(numMap, denMap, minDenom = 15, scale = 1, isAsc = false) {
            if (!numMap || !denMap) return [];
            return Object.keys(numMap)
                .filter(key => (denMap[key] || 0) >= minDenom)
                .map(key => {
                    const denominator = denMap[key] || 1;
                    return { 
                        name: `${key}组`, 
                        value: (numMap[key] / denominator) * scale 
                    };
                })
                .sort((a, b) => isAsc ? a.value - b.value : b.value - a.value)
                .slice(0, TOP_N)
                .reverse(); 
        }

        function renderAllCharts(data) {
            chartInstances.forEach(chart => chart.dispose());
            chartInstances = [];

            // 1. 和 NONE 对局次数
            createBarChart('chart-withNone', '和 NONE 对局次数排行', 
                getTopN(data.withNoneTimes), '次', '#5470c6');
                
            // [新增] 2. 和 BOT 对局次数
            createBarChart('chart-withBot', '和 BOT 对局次数排行', 
                getTopN(data.withBotTimes), '次', '#73a0fa'); // 使用了略有区别的蓝色以作区分
            
            // 3. 和其他玩家对局次数
            createBarChart('chart-withOther', '与其他玩家对局次数排行', 
                getTopN(data.withOtherTimes), '次', '#91cc75');
            
            // 4. 抓人次数
            createBarChart('chart-catch', '抓人总次数排行', 
                getTopN(data.catchTimes), '次', '#fac858');
            
            // 5. 拿旗次数
            createBarChart('chart-flag', '拿旗总次数排行', 
                getTopN(data.getFlagTimes), '次', '#ee6666');
            
            // 6. 获胜次数
            createBarChart('chart-win', '获胜总次数排行', 
                getTopN(data.winTimes), '次', '#73c0de');
            
            // 7. 总游戏时间 
            createBarChart('chart-totalTime', '总游戏时间排行', 
                getTopN(data.totalTime, 1/1000), '秒', '#3ba272');
            
            // 8. 平均胜率 
            createBarChart('chart-avgWinRate', '平均胜率排行 (≥15场)', 
                getTopNAvg(data.winTimes, data.withOtherTimes, 15, 100, false), '%', '#fc8452', true);
            
            // 9. 场均抓人次数
            createBarChart('chart-avgCatch', '场均抓人次数排行 (≥15场)', 
                getTopNAvg(data.catchTimes, data.withOtherTimes, 15, 1, false), '次', '#9a60b4', true);
            
            // 10. 场均拿旗次数
            createBarChart('chart-avgFlag', '场均拿旗次数排行 (≥15场)', 
                getTopNAvg(data.getFlagTimes, data.withOtherTimes, 15, 1, false), '次', '#ea7ccc', true);
            
            // 11. 平均游戏时间最短 
            createBarChart('chart-avgShortestTime', '平均每局耗时最短排行 (≥15场)', 
                getTopNAvg(data.totalTime, data.withOtherTimes, 15, 1/1000, true), '秒', '#5470c6', true);
        }

        function createBarChart(elementId, title, chartData, unit, color, isFloat = false) {
            const chartDom = document.getElementById(elementId);
            const myChart = echarts.init(chartDom);
            
            const formatValue = (val) => isFloat ? val.toFixed(2) : Math.round(val);

            const option = {
                title: {
                    text: title,
                    left: 'center',
                    textStyle: { fontSize: 15, color: '#333' }
                },
                tooltip: {
                    trigger: 'axis',
                    axisPointer: { type: 'shadow' },
                    formatter: function (params) {
                        const data = params[0];
                        return `${data.name}<br/>${data.marker} ${formatValue(data.value)}${unit}`;
                    }
                },
                grid: {
                    left: '3%',
                    right: '10%',
                    bottom: '5%',
                    containLabel: true
                },
                xAxis: {
                    type: 'value',
                    axisLabel: { formatter: `{value} ${unit}` },
                    splitLine: { lineStyle: { type: 'dashed' } }
                },
                yAxis: {
                    type: 'category',
                    data: chartData.map(item => item.name),
                    axisLabel: { fontWeight: 'bold' }
                },
                series: [
                    {
                        type: 'bar',
                        data: chartData.map(item => item.value),
                        itemStyle: { color: color, borderRadius: [0, 5, 5, 0] },
                        label: {
                            show: true,
                            position: 'right',
                            formatter: (params) => `${formatValue(params.value)}${unit}`,
                            color: '#666'
                        },
                        barMaxWidth: 30
                    }
                ]
            };

            if (chartData.length === 0) {
                option.title.subtext = "暂无符合条件的数据";
                option.title.subtextStyle = { color: 'red' };
            }

            myChart.setOption(option);
            chartInstances.push(myChart);
        }
    </script>
</body>
</html>
"""