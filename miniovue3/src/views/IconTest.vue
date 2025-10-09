<template>
  <div class="icon-test">
    <h2>图标测试页面</h2>
    <div class="icon-grid">
      <div class="icon-item">
        <el-icon size="32"><Document /></el-icon>
        <span>Document</span>
      </div>
      <div class="icon-item">
        <el-icon size="32"><Picture /></el-icon>
        <span>Picture</span>
      </div>
      <div class="icon-item">
        <el-icon size="32"><Check /></el-icon>
        <span>Check</span>
      </div>
      <div class="icon-item">
        <el-icon size="32"><Warning /></el-icon>
        <span>Warning</span>
      </div>
      <div class="icon-item">
        <el-icon size="32"><Refresh /></el-icon>
        <span>Refresh</span>
      </div>
      <div class="icon-item">
        <el-icon size="32"><Star /></el-icon>
        <span>Star</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Document, Picture, Check, Warning, Refresh, Star } from '@element-plus/icons-vue';
</script>

<style scoped>
.icon-test {
  padding: 20px;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 15px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: #f9f9f9;
}

.icon-item span {
  margin-top: 8px;
  font-size: 14px;
  color: #666;
}
</style>