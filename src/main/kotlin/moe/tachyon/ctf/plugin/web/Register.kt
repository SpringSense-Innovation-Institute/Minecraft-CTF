package moe.tachyon.ctf.plugin.web

import org.intellij.lang.annotations.Language

@Language("HTML")
const val registerHtml = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>服务器队伍注册系统</title>
    <!-- 使用国内稳定的 Staticfile CDN -->
    <link href="https://cdn.staticfile.net/twitter-bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
    <script src="https://cdn.staticfile.net/vue/3.3.4/vue.global.prod.min.js"></script>
    <script src="https://cdn.staticfile.net/axios/1.4.0/axios.min.js"></script>
    <style>
        body { background-color: #f4f6f9; padding-bottom: 50px; }
        .navbar { margin-bottom: 30px; box-shadow: 0 2px 4px rgba(0,0,0,.1); }
        .card { border-radius: 10px; border: none; box-shadow: 0 4px 6px rgba(0,0,0,.05); }
        .group-card { transition: transform 0.2s; }
        .group-card:hover { transform: translateY(-5px); }
        .player-badge { font-weight: normal; font-size: 0.9em; }
    </style>
</head>
<body>

<div id="app">
    <!-- 导航栏 -->
    <nav class="navbar navbar-expand-lg navbar-dark bg-primary">
        <div class="container">
            <a class="navbar-brand" href="#">⚔️ 赛事队伍注册</a>
        </div>
    </nav>

    <div class="container">
        <!-- 提示信息区 -->
        <div v-if="alert.show" class="alert alert-dismissible fade show" :class="'alert-' + alert.type" role="alert">
            {{ alert.message }}
            <button type="button" class="btn-close" @click="alert.show = false"></button>
        </div>

        <div class="row">
            <!-- 左侧：注册表单 -->
            <div class="col-lg-4 mb-4">
                <div class="card">
                    <div class="card-header bg-white text-primary fw-bold">
                        ➕ 注册新队伍 (需3人)
                    </div>
                    <div class="card-body">
                        <form @submit.prevent="submitGroup">
                            <div class="mb-3">
                                <label class="form-label">队伍名称</label>
                                <input type="text" class="form-control" v-model="form.name" placeholder="请输入队伍名称" required>
                            </div>
                            <hr>
                            <div class="mb-3">
                                <label class="form-label">队员 1 (真实姓名)</label>
                                <input type="text" class="form-control" v-model="form.player1" placeholder="队长姓名" required>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">队员 2 (真实姓名)</label>
                                <input type="text" class="form-control" v-model="form.player2" placeholder="队员姓名" required>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">队员 3 (真实姓名)</label>
                                <input type="text" class="form-control" v-model="form.player3" placeholder="队员姓名" required>
                            </div>
                            
                            <button type="submit" class="btn btn-primary w-100" :disabled="isSubmitting">
                                {{ isSubmitting ? '注册中...' : '提交注册' }}
                            </button>
                        </form>
                    </div>
                </div>
            </div>

            <!-- 右侧：已有队伍列表 -->
            <div class="col-lg-8">
                <div class="card">
                    <div class="card-header bg-white text-success fw-bold d-flex justify-content-between align-items-center">
                        <span>🏆 已注册队伍 ({{ groups.length }})</span>
                        <button class="btn btn-sm btn-outline-success" @click="fetchGroups">刷新列表</button>
                    </div>
                    <div class="card-body">
                        <div v-if="isLoading" class="text-center text-muted my-5">
                            <div class="spinner-border text-success" role="status"></div>
                            <div class="mt-2">正在加载队伍信息...</div>
                        </div>
                        
                        <div v-else-if="groups.length === 0" class="text-center text-muted my-5">
                            暂无队伍注册，快来抢占沙发吧！
                        </div>

                        <div class="row" v-else>
                            <div class="col-md-6 mb-3" v-for="group in groups" :key="group.id">
                                <div class="card group-card h-100 bg-light">
                                    <div class="card-body">
                                        <h5 class="card-title text-primary">#{{ group.id }} {{ group.name }}</h5>
                                        <hr>
                                        <ul class="list-group list-group-flush">
                                            <li class="list-group-item bg-transparent px-0 py-1" v-for="player in group.players" :key="player.id">
                                                👤 {{ player.name }} 
                                                <span class="badge bg-secondary player-badge float-end">ID: {{ player.id }}</span>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
    const { createApp, ref, reactive, onMounted } = Vue;

    createApp({
        setup() {
            // 状态定义
            const groups = ref([]);
            const isLoading = ref(false);
            const isSubmitting = ref(false);
            const form = reactive({
                name: '',
                player1: '',
                player2: '',
                player3: ''
            });
            const alert = reactive({
                show: false,
                type: 'success',
                message: ''
            });

            // 提示信息处理
            const showAlert = (message, type = 'success') => {
                alert.message = message;
                alert.type = type;
                alert.show = true;
                // 5秒后自动关闭提示
                setTimeout(() => { alert.show = false; }, 5000);
            };

            // 获取队伍列表
            const fetchGroups = async () => {
                isLoading.value = true;
                try {
                    const response = await axios.get('/groups');
                    groups.value = response.data;
                } catch (error) {
                    showAlert('获取队伍列表失败: ' + (error.response?.data || error.message), 'danger');
                } finally {
                    isLoading.value = false;
                }
            };

            // 提交队伍注册
            const submitGroup = async () => {
                // 校验：防止填入重复的名字
                const playerNames = [form.player1.trim(), form.player2.trim(), form.player3.trim()];
                const uniquePlayers = new Set(playerNames);
                if (uniquePlayers.size !== 3) {
                    showAlert('队伍内不能有重复的玩家姓名！', 'warning');
                    return;
                }

                isSubmitting.value = true;
                const payload = {
                    name: form.name.trim(),
                    players: playerNames
                };

                try {
                    // Axios 默认发送 application/json
                    await axios.post('/registerGroup', payload);
                    
                    showAlert(`队伍 "${'$'}{payload.name}" 注册成功！`);
                    
                    // 清空表单
                    form.name = '';
                    form.player1 = '';
                    form.player2 = '';
                    form.player3 = '';
                    
                    // 重新加载列表
                    fetchGroups();
                } catch (error) {
                    // 后端抛出的 error("...") 会被捕捉并以文本形式返回 400
                    const errorMsg = error.response?.data || '网络错误或服务器无响应';
                    showAlert(`注册失败: ${'$'}{errorMsg}`, 'danger');
                } finally {
                    isSubmitting.value = false;
                }
            };

            // 页面加载时自动获取一次数据
            onMounted(() => {
                fetchGroups();
            });

            return {
                groups,
                isLoading,
                isSubmitting,
                form,
                alert,
                fetchGroups,
                submitGroup
            };
        }
    }).mount('#app');
</script>

</body>
</html>
"""