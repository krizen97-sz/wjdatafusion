<template>
  <div class="app-container support-version-page">
    <section class="version-hero">
      <div class="version-hero__copy">
        <span class="version-hero__eyebrow">现场融合管理</span>
        <h2>功能版本记录</h2>
        <p>记录现场融合管理每次功能迭代、交互调整和数据结构变化，便于部署、验收和回溯。</p>
      </div>
      <div class="version-hero__meta">
        <span>
          <strong>{{ latestRelease.version }}</strong>
          <em>当前版本</em>
        </span>
        <span>
          <strong>{{ majorReleaseCount }}</strong>
          <em>大版本</em>
        </span>
        <span>
          <strong>{{ releaseNotes.length }}</strong>
          <em>记录数</em>
        </span>
      </div>
    </section>

    <section class="version-workspace">
      <aside class="version-index">
        <button
          v-for="entry in releaseNotes"
          :key="entry.version"
          type="button"
          class="version-index-item"
          :class="{ 'is-active': activeVersion === entry.version }"
          @click="activeVersion = entry.version"
        >
          <span>{{ entry.version }}</span>
          <strong>{{ entry.title }}</strong>
          <em>{{ entry.submitTime }} · {{ entry.levelLabel }}</em>
        </button>
      </aside>

      <main class="version-detail">
        <div class="version-detail__head">
          <div>
            <span class="version-detail__date">提交时间 {{ activeRelease.submitTime }}</span>
            <h3>{{ activeRelease.version }} {{ activeRelease.title }}</h3>
            <p>{{ activeRelease.summary }}</p>
          </div>
          <el-tag :type="activeRelease.tagType" effect="light">{{ activeRelease.levelLabel }}</el-tag>
        </div>

        <div class="version-detail__body">
          <section class="version-detail-section">
            <strong>修改内容</strong>
            <ul>
              <li v-for="item in activeRelease.changes" :key="item">{{ item }}</li>
            </ul>
          </section>

          <section class="version-detail-section">
            <strong>影响范围</strong>
            <div class="version-scope-list">
              <span v-for="item in activeRelease.scope" :key="item">{{ item }}</span>
            </div>
          </section>

          <section class="version-detail-section">
            <strong>数据库修改脚本</strong>
            <p>{{ activeRelease.database }}</p>
            <div v-if="activeRelease.scripts && activeRelease.scripts.length" class="version-script-list">
              <span v-for="script in activeRelease.scripts" :key="script">{{ script }}</span>
            </div>
            <div v-else class="version-script-empty">无数据库脚本</div>
          </section>
        </div>
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { latestSupportRelease, releaseNotes } from './releaseNotes'

const activeVersion = ref(latestSupportRelease.version)
const latestRelease = computed(() => latestSupportRelease)
const majorReleaseCount = computed(() => releaseNotes.filter((item) => item.level === 'major').length)
const activeRelease = computed(() => releaseNotes.find((item) => item.version === activeVersion.value) || releaseNotes[0])
</script>

<style scoped>
.support-version-page {
  display: grid;
  gap: 18px;
  color: #17314d;
}

.version-hero {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 28px;
  border: 1px solid #d9e6f3;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(235, 247, 255, 0.94), rgba(245, 251, 247, 0.94)),
    #f6fbff;
}

.version-hero__copy {
  display: grid;
  align-content: center;
  gap: 8px;
  min-width: 0;
}

.version-hero__eyebrow,
.version-detail__date {
  color: #2f7f62;
  font-size: 13px;
  font-weight: 700;
}

.version-hero h2,
.version-detail h3 {
  margin: 0;
  color: #132c47;
  line-height: 1.2;
}

.version-hero h2 {
  font-size: 30px;
}

.version-hero p,
.version-detail p {
  margin: 0;
  color: #637d98;
  font-size: 14px;
  line-height: 1.7;
}

.version-hero__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(88px, 1fr));
  gap: 10px;
  min-width: 330px;
}

.version-hero__meta span {
  display: grid;
  place-items: center;
  min-height: 88px;
  border: 1px solid rgba(143, 188, 169, 0.58);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
}

.version-hero__meta strong {
  color: #2f7f62;
  font-size: 22px;
  line-height: 1;
}

.version-hero__meta em {
  margin-top: 8px;
  color: #71879c;
  font-size: 12px;
  font-style: normal;
}

.version-workspace {
  border: 1px solid #d9e6f3;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
}

.version-workspace {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  min-height: 520px;
  overflow: hidden;
}

.version-index {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 14px;
  border-right: 1px solid #e1ebf5;
  background: #f8fbff;
}

.version-index-item {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #617891;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.version-index-item:hover,
.version-index-item.is-active {
  border-color: #b8d3ef;
  background: #ffffff;
  color: #1f5ea8;
}

.version-index-item span,
.version-index-item strong,
.version-index-item em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-index-item span {
  color: #2f7f62;
  font-size: 12px;
  font-weight: 800;
}

.version-index-item strong {
  color: inherit;
  font-size: 14px;
}

.version-index-item em {
  color: #7b8fa5;
  font-size: 12px;
  font-style: normal;
}

.version-detail {
  display: grid;
  align-content: start;
  gap: 20px;
  padding: 24px;
}

.version-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid #e1ebf5;
}

.version-detail h3 {
  margin-top: 6px;
  margin-bottom: 8px;
  font-size: 24px;
}

.version-detail__body {
  display: grid;
  gap: 18px;
}

.version-detail-section {
  display: grid;
  gap: 10px;
}

.version-detail-section strong {
  color: #17314d;
  font-size: 15px;
}

.version-detail-section ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  color: #425d78;
  line-height: 1.7;
}

.version-scope-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.version-scope-list span {
  padding: 6px 10px;
  border: 1px solid #d8e6f3;
  border-radius: 999px;
  background: #f8fbff;
  color: #45627f;
  font-size: 12px;
  font-weight: 700;
}

.version-script-list {
  display: grid;
  gap: 8px;
}

.version-script-list span,
.version-script-empty {
  min-width: 0;
  padding: 9px 11px;
  border: 1px solid #d8e6f3;
  border-radius: 8px;
  background: #f8fbff;
  color: #365673;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.version-script-empty {
  color: #7b8fa5;
  font-family: inherit;
}

@media (max-width: 1100px) {
  .version-hero,
  .version-detail__head {
    flex-direction: column;
  }

  .version-hero__meta {
    width: 100%;
    min-width: 0;
  }

  .version-workspace {
    grid-template-columns: 1fr;
  }

  .version-index {
    border-right: 0;
    border-bottom: 1px solid #e1ebf5;
  }
}

@media (max-width: 760px) {
  .version-hero__meta {
    grid-template-columns: 1fr;
  }
}
</style>
