package moe.tachyon.ctf.plugin.web

import org.intellij.lang.annotations.Language

@Language("HTML")
const val viewerHtml = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SSE Game Map with Cows</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: #f0f2f5;
            margin: 0;
            padding: 20px;
            display: flex;
            flex-direction: column;
            align-items: center;
            height: 100vh;
            box-sizing: border-box;
        }

        .card {
            background: #fff;
            padding: 30px;
            border-radius: 12px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
            text-align: center;
            max-width: 400px;
            width: 100%;
        }

        h1 { margin-top: 0; color: #333; }
        .form-group { margin-bottom: 15px; text-align: left; }
        label { display: block; margin-bottom: 5px; font-weight: bold; color: #555; }
        
        input[type="text"], input[type="number"] {
            width: 100%; padding: 10px; border: 1px solid #ccc;
            border-radius: 6px; box-sizing: border-box;
        }

        button {
            width: 100%; padding: 12px; background-color: #1890ff;
            color: white; border: none; border-radius: 6px;
            font-size: 16px; cursor: pointer; transition: background-color 0.3s;
        }
        button:hover { background-color: #096dd9; }

        #game-container {
            display: none;
            width: 100%;
            height: 100%;
            text-align: center;
            flex-direction: column;
            align-items: center;
        }

        #team-display {
            font-size: 24px;
            font-weight: bold;
            margin-bottom: 10px;
            padding: 10px 20px;
            border-radius: 8px;
            display: inline-block;
            flex-shrink: 0;
            transition: opacity 0.3s;
            white-space: pre-wrap;
        }

        .team-red { background-color: #ffccc7; color: #cf1322; border: 1px solid #ffa39e; }
        .team-blue { background-color: #bae0ff; color: #096dd9; border: 1px solid #91caff; }

        .canvas-wrapper {
            flex-grow: 1;
            width: 100%;
            display: flex;
            justify-content: center;
            align-items: center;
            overflow: hidden;
        }

        canvas {
            background-color: #fff;
            border: 2px solid #333;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
            border-radius: 4px;
            max-width: 100%;
            max-height: 100%;
            object-fit: contain; /* 保持比例缩放完整显示 */
        }
    </style>
</head>
<body>

    <!-- 配置与入口界面 -->
    <div id="setup-container" class="card">
        <h1>进入对局</h1>
        <div class="form-group" style='display: none'>
            <label for="hostInput">SSE 接口主机与 URI</label>
            <input type="text" id="hostInput" value="http://localhost:8080/events">
        </div>
        <div class="form-group">
            <label for="groupIdInput">Group ID</label>
            <input type="number" id="groupIdInput" placeholder="请输入数字 ID">
        </div>
        <button onclick="connectGame()">连接</button>
    </div>

    <!-- 游戏地图界面 -->
    <div id="game-container">
        <div id="team-display">等待分配队伍...</div>
        <div class="canvas-wrapper">
            <canvas id="gameCanvas"></canvas>
        </div>
    </div>

    <script>
        const CELL_SIZE = 50; // 基础格子大小，用于内部高分辨率绘制
        let sseConnection = null;
        const canvas = document.getElementById('gameCanvas');
        const ctx = canvas.getContext('2d');

        // 颜色定义
        const COLOR_COW = '#ffdbac'; // 肉色 (浅肤色)
        const COLOR_COW_STROKE = '#855e42'; // 奶牛描边，使其更清晰

        function connectGame() {
//            const host = document.getElementById('hostInput').value.trim();
            const host = window.location.origin + "/sse";
            const groupId = document.getElementById('groupIdInput').value.trim();

            if (!host || !groupId) return alert('请填写完整信息');

            const url = new URL(host);
            url.searchParams.append('group', groupId);

            // 切换 UI
            document.getElementById('setup-container').style.display = 'none';
            document.getElementById('game-container').style.display = 'flex';

            if (sseConnection) sseConnection.close();
            sseConnection = new EventSource(url.toString());

            // --- 增加自动重连的 UI 提示逻辑 ---
            sseConnection.onopen = () => {
                const teamEl = document.getElementById('team-display');
                if (teamEl.innerText.includes('重连中')) {
                    teamEl.innerText = teamEl.dataset.originalText || '✅ 连接成功';
                    teamEl.style.opacity = '1';
                }
            };

            sseConnection.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    renderGame(data); // 核心渲染
                } catch (err) {
                    console.error('JSON 解析失败:', err);
                }
            };

            sseConnection.onerror = (err) => {
                const teamEl = document.getElementById('team-display');
                // readyState === 0 表示浏览器正在尝试自动重连
                if (sseConnection.readyState === EventSource.CONNECTING) {
                    if (!teamEl.dataset.originalText && teamEl.innerText !== '等待分配队伍...') {
                        teamEl.dataset.originalText = teamEl.innerText;
                    }
                    teamEl.innerText = '⚠️ 网络波动，重连中...';
                    teamEl.style.opacity = '0.6';
                } else if (sseConnection.readyState === EventSource.CLOSED) {
                    teamEl.innerText = '❌ 连接已断开，请刷新';
                    teamEl.className = '';
                }
            };
        }

        // 主渲染函数
        function renderGame(data) {
            // 1. 解构新数据结构，增加了 cows
            const { myTeam, map, players, cows, leftScore, rightScore } = data;

            // 2. 更新队伍显示
            const teamEl = document.getElementById('team-display');
            // 如果正在重连中，不覆盖重连提示
            if (sseConnection.readyState === EventSource.OPEN) {
                if (myTeam === 'red') {
                    teamEl.className = 'team-red';
                    teamEl.innerText = '🔴 你是红队 (RED)\n你:对手=' + leftScore + ':' + rightScore;
                } else if (myTeam === 'blue') {
                    teamEl.className = 'team-blue';
                    teamEl.innerText = '🔵 你是蓝队 (BLUE)\n你:对手=' + rightScore + ':' + leftScore;
                }
            }

            // 3. 初始化画布尺寸 (逻辑：x轴向上，y轴向右)
            const mapWidth = map.length;       // x轴逻辑长度
            const mapHeight = map[0].length;   // y轴逻辑长度
            
            // 物理画布宽度 = y轴长度，物理画布高度 = x轴长度
            canvas.width = mapHeight * CELL_SIZE;   
            canvas.height = mapWidth * CELL_SIZE;   

            ctx.clearRect(0, 0, canvas.width, canvas.height);

            // 4. 绘制底层地图网格
            for (let x = 0; x < mapWidth; x++) {
                for (let y = 0; y < mapHeight; y++) {
                    drawCell(x, y, map[x][y], mapWidth);
                }
            }

            // 4.5 绘制中心标记线 (x = max_x / 2)
            // 因为 canvas 的物理高度等于 mapWidth * CELL_SIZE，所以中线恰好在 canvas.height / 2 的位置
            ctx.save();
            ctx.beginPath();
            const centerY = canvas.height / 2;
            ctx.moveTo(0, centerY);
            ctx.lineTo(canvas.width, centerY);
            ctx.strokeStyle = 'rgba(0, 0, 0, 0.6)'; // 半透明深色虚线
            ctx.lineWidth = 3;
            ctx.setLineDash([10, 8]); // 设置虚线样式：10px 实线，8px 空隙
            ctx.stroke();
            ctx.restore();

            // 5. 绘制奶牛 (Cows) - 在玩家之下，网格之上
            if (cows && Array.isArray(cows)) {
                cows.forEach(cow => {
                    drawCow(cow.x, cow.y, mapWidth);
                });
            }

            // 6. 绘制玩家 (Players)
            if (players) {
                for (const key in players) {
                    if (players.hasOwnProperty(key)) {
                        const p = players[key];
                        drawPlayer(p.x, p.y, p.team, mapWidth);
                    }
                }
            }
        }

        // 绘制单个格子（x轴向上，y轴向右映射）
        function drawCell(x, y, type, mapWidth) {
            // 物理 x = y
            // 物理 y = (总x长度 - 1 - x)
            const cx = y * CELL_SIZE; 
            const cy = (mapWidth - 1 - x) * CELL_SIZE; 
            
            const halfSize = CELL_SIZE / 2;
            const offset = CELL_SIZE / 4;

            ctx.fillStyle = '#ffffff';
            ctx.fillRect(cx, cy, CELL_SIZE, CELL_SIZE);

            if (type === 'OBSTACLE') {
                ctx.fillStyle = '#333333';
                ctx.fillRect(cx, cy, CELL_SIZE, CELL_SIZE);
            } else if (type !== 'BLANK') {
                if (type.includes('TARGET')) {
                    ctx.fillStyle = '#ffd700'; // 金色
                    ctx.fillRect(cx, cy, CELL_SIZE, CELL_SIZE);
                    ctx.fillStyle = '#ffffff'; // 内圈白
                    ctx.fillRect(cx + offset, cy + offset, halfSize, halfSize);
                }

                if (type === 'LEFT_FLAG' || type === 'LEFT_TARGET_WITH_FLAG') {
                    ctx.fillStyle = '#cf1322'; // 红旗
                    ctx.fillRect(cx + offset, cy + offset, halfSize, halfSize);
                } else if (type === 'RIGHT_FLAG' || type === 'RIGHT_TARGET_WITH_FLAG') {
                    ctx.fillStyle = '#096dd9'; // 蓝旗
                    ctx.fillRect(cx + offset, cy + offset, halfSize, halfSize);
                }
            }

            ctx.strokeStyle = '#e8e8e8'; // 网格线
            ctx.lineWidth = 1;
            ctx.strokeRect(cx, cy, CELL_SIZE, CELL_SIZE);
        }

        // --- 新增：绘制奶牛 (肉色三角形，支持浮点坐标) ---
        function drawCow(x, y, mapWidth) {
            // 坐标映射逻辑与玩家一致
            const px = y * CELL_SIZE;
            const py = (mapWidth - x) * CELL_SIZE; // x越大越向上（物理y越小）

            // 三角形大小，设为格子大小的 40%
            const size = CELL_SIZE * 0.4; 

            ctx.save(); // 保存当前绘图状态
            ctx.translate(px, py); // 将坐标系移到奶牛中心，方便绘制

            ctx.beginPath();
            // 绘制一个尖角向上的三角形 (相对于现在的旋转系)
            // 默认坐标系旋转了，这里绘图需要适应。
            // 现在的物理画布：上是-Y。我们让三角形尖角指向逻辑X轴正方向（即物理画布的上方）。
            ctx.moveTo(0, -size);           // 顶点 (尖角)
            ctx.lineTo(-size * 0.86, size/2); // 左下角 (sin60°≈0.86)
            ctx.lineTo(size * 0.86, size/2);  // 右下角
            ctx.closePath();

            // 填充肉色
            ctx.fillStyle = COLOR_COW;
            ctx.fill();

            // 深色描边，防止在白色或金底上看不清
            ctx.strokeStyle = COLOR_COW_STROKE;
            ctx.lineWidth = 2;
            ctx.lineJoin = 'round'; // 使尖角圆润一点
            ctx.stroke();

            ctx.restore(); // 恢复绘图状态
        }

        // 绘制玩家圆点
        function drawPlayer(x, y, team, mapWidth) {
            const px = y * CELL_SIZE;
            const py = (mapWidth - x) * CELL_SIZE; 
            const radius = CELL_SIZE * 0.3;

            ctx.beginPath();
            ctx.arc(px, py, radius, 0, Math.PI * 2);
            ctx.fillStyle = team === 'red' ? '#ff4d4f' : '#40a9ff';
            ctx.fill();
            
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 2;
            ctx.stroke();
        }
    </script>
</body>
</html>
"""