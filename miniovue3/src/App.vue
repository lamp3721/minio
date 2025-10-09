<template>
  <div id="app" class="app-container">
    <el-menu
        :default-active="activeIndex"
        class="main-menu"
        mode="horizontal"
        :router="true"
        @select="handleSelect"
    >
      <el-menu-item index="/upload" class="main-menu-item">
        <el-icon><Star /></el-icon>
        文件上传管理
      </el-menu-item>
      
    </el-menu>
    <div class="content-container">
      <router-view></router-view>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { Star } from '@element-plus/icons-vue';

const route = useRoute();
const activeIndex = ref(route.path);

// 监听路由变化，以确保导航菜单的高亮状态能够正确地同步更新
watch(() => route.path, (newPath) => {
  activeIndex.value = newPath;
});

const handleSelect = (key) => {
  activeIndex.value = key;
};
</script>

<style>
/* 全局样式 */
body {
  margin: 0;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  background-color: #f5f7fa;
}

.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.main-menu {
  border-bottom: solid 1px var(--el-menu-border-color);
}

.content-container {
  flex-grow: 1;
  padding: 20px;
  overflow-y: auto; /* 如果页面内容超出视口高度，则显示垂直滚动条 */
}

/* 
  定义一些通用的卡片和头部样式，这些样式可以被子页面（视图组件）继承或复用，
  以保持整体UI风格的一致性。
*/
.box-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 主菜单项样式 */
.main-menu-item {
  font-weight: 600;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent !important;
}

.main-menu-item:hover {
  background: linear-gradient(135deg, #337ecc 0%, #529b2e 100%);
  background-clip: text;
  -webkit-background-clip: text;
}


</style>
