<template>
  <div id="app" class="app-container">
    <el-menu
        :default-active="activeIndex"
        class="main-menu"
        mode="horizontal"
        :router="true"
        @select="handleSelect"
    >
      <el-menu-item index="/private">私有文件库</el-menu-item>
      <el-menu-item index="/public">公共资源库</el-menu-item>
    </el-menu>
    <div class="content-container">
      <router-view></router-view>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const activeIndex = ref(route.path);

// 监听路由变化，以确保导航菜单的高亮状态能正确更新
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
  overflow-y: auto; /* 如果内容超长，则显示滚动条 */
}

/* 一些通用的卡片和头部样式，可以被子页面继承 */
.box-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
