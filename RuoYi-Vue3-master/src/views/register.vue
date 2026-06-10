<template>
  <div class="register">
    <div class="background-container">
      <div class="gradient-overlay"></div>
      <div class="particles">
        <div class="particle" v-for="n in 20" :key="n" :style="{top: `${Math.random() * 100}%`, left: `${Math.random() * 100}%`}"></div>
      </div>
    </div>

    <div class="container">
      <div class="left-section">
        <div class="welcome-content">
          <div class="logo-area">
            <div class="logo-circle">
              <svg class="logo-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <circle cx="9" cy="7" r="4" stroke="currentColor" stroke-width="2"/>
                <line x1="19" y1="8" x2="19" y2="14" stroke="currentColor" stroke-width="2"/>
                <line x1="22" y1="11" x2="16" y2="11" stroke="currentColor" stroke-width="2"/>
              </svg>
            </div>
            <h1 class="platform-name">{{ title }}</h1>
          </div>
          <div class="tagline">
            <p>武进项目部资源信息平台</p>
            <p class="subtitle">安全、可靠、智能的安防解决方案</p>
          </div>
        </div>
      </div>

      <div class="right-section">
        <div class="register-card">
          <div class="card-header">
            <h2 class="card-title">创建账户</h2>
            <p class="card-subtitle">请输入您的账户信息</p>
          </div>

          <el-form ref="registerRef" :model="registerForm" :rules="registerRules" class="register-form" @keyup.enter="handleRegister">
            <el-form-item prop="username">
              <div class="input-wrapper">
                <label class="input-label">用户名</label>
                <el-input
                  v-model="registerForm.username"
                  type="text"
                  size="large"
                  class="rounded-input"
                >
                  <template #prefix>
                    <svg-icon icon-class="user" class="input-icon" />
                  </template>
                </el-input>
              </div>
            </el-form-item>

            <el-form-item prop="password">
              <div class="input-wrapper">
                <label class="input-label">密码</label>
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  size="large"
                  class="rounded-input"
                >
                  <template #prefix>
                    <svg-icon icon-class="password" class="input-icon" />
                  </template>
                </el-input>
              </div>
            </el-form-item>

            <el-form-item prop="confirmPassword">
              <div class="input-wrapper">
                <label class="input-label">确认密码</label>
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  size="large"
                  class="rounded-input"
                >
                  <template #prefix>
                    <svg-icon icon-class="password" class="input-icon" />
                  </template>
                </el-input>
              </div>
            </el-form-item>

            <el-form-item prop="code" v-if="captchaEnabled">
              <div class="input-wrapper">
                <label class="input-label">验证码</label>
                <div class="captcha-row">
                  <el-input
                    v-model="registerForm.code"
                    size="large"
                    class="rounded-input captcha-input"
                  >
                    <template #prefix>
                      <svg-icon icon-class="validCode" class="input-icon" />
                    </template>
                  </el-input>
                  <div class="captcha-box" @click="getCode">
                    <img :src="codeUrl" class="captcha-img" alt="验证码"/>
                  </div>
                </div>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button
                :loading="loading"
                type="primary"
                size="large"
                class="submit-button"
                @click="handleRegister"
              >
                <span v-if="!loading">注 册</span>
                <span v-else>注 册 中...</span>
              </el-button>
            </el-form-item>

            <div class="login-link">
              <span>已有账户？</span>
              <router-link to="/login" class="signin-link">立即登录</router-link>
            </div>
          </el-form>
        </div>
      </div>
    </div>

    <div class="footer">
      <p>{{ footerContent }}</p>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox } from "element-plus"
import { getCodeImg, register } from "@/api/login"
import defaultSettings from '@/settings'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const router = useRouter()
const { proxy } = getCurrentInstance()

const registerForm = ref({
  username: "",
  password: "",
  confirmPassword: "",
  code: "",
  uuid: ""
})

const equalToPassword = (rule, value, callback) => {
  if (registerForm.value.password !== value) {
    callback(new Error("两次输入的密码不一致"))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, trigger: "blur", message: "请输入您的账号" },
    { min: 2, max: 20, message: "用户账号长度必须介于 2 和 20 之间", trigger: "blur" }
  ],
  password: [
    { required: true, trigger: "blur", message: "请输入您的密码" },
    { min: 5, max: 20, message: "用户密码长度必须介于 5 和 20 之间", trigger: "blur" },
    { pattern: /^[^<>"'|\\]+$/, message: "不能包含非法字符：< > \" ' \\\ |", trigger: "blur" }
  ],
  confirmPassword: [
    { required: true, trigger: "blur", message: "请再次输入您的密码" },
    { required: true, validator: equalToPassword, trigger: "blur" }
  ],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
const captchaEnabled = ref(true)

function handleRegister() {
  proxy.$refs.registerRef.validate(valid => {
    if (valid) {
      loading.value = true
      register(registerForm.value).then(res => {
        const username = registerForm.value.username
        ElMessageBox.alert("<font color='red'>恭喜你，您的账号 " + username + " 注册成功！</font>", "系统提示", {
          dangerouslyUseHTMLString: true,
          type: "success",
        }).then(() => {
          router.push("/login")
        }).catch(() => {})
      }).catch(() => {
        loading.value = false
        if (captchaEnabled) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      registerForm.value.uuid = res.uuid
    }
  })
}

getCode()
</script>

<style lang='scss' scoped>
.register {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  position: relative;
  overflow: hidden;
}

.background-container {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  pointer-events: none;
}

.gradient-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(circle at 20% 80%, rgba(103, 232, 249, 0.1) 0%, transparent 50%),
              radial-gradient(circle at 80% 20%, rgba(148, 163, 184, 0.1) 0%, transparent 50%);
  z-index: 0;
}

.particles {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
}

.particle {
  position: absolute;
  width: 2px;
  height: 2px;
  background: rgba(103, 232, 249, 0.6);
  border-radius: 50%;
  animation: float 6s infinite ease-in-out;
  box-shadow: 0 0 10px rgba(103, 232, 249, 0.5);

  @for $i from 1 through 20 {
    &:nth-child(#{$i}) {
      animation-delay: #{random(10) - 5}s;
      opacity: #{random(10) / 15};
    }
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) translateX(0);
  }
  50% {
    transform: translateY(-20px) translateX(10px);
  }
}

.container {
  display: flex;
  flex: 1;
  position: relative;
  z-index: 2;
}

.left-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: linear-gradient(135deg, rgba(2, 132, 199, 0.05) 0%, rgba(103, 232, 249, 0.05) 100%);
}

.welcome-content {
  max-width: 500px;
  text-align: center;
}

.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 30px;
}

.logo-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  box-shadow: 0 10px 25px rgba(2, 132, 199, 0.2);
}

.logo-icon {
  width: 40px;
  height: 40px;
  color: white;
}

.platform-name {
  color: #0ea5e9;
  font-size: 28px;
  font-weight: 700;
  margin: 0;
  text-align: center;
}

.tagline {
  p {
    margin: 10px 0;
    color: #64748b;

    &.subtitle {
      font-size: 16px;
      color: #94a3b8;
      font-style: italic;
    }
  }
}

.right-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.register-card {
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(2, 132, 199, 0.1);
}

.card-header {
  text-align: center;
  margin-bottom: 30px;
}

.card-title {
  color: #1e293b;
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.card-subtitle {
  color: #64748b;
  font-size: 14px;
  margin: 0;
}

.register-form {
  :deep(.el-form-item) {
    margin-bottom: 24px;
  }
}

.input-wrapper {
  position: relative;
  margin-bottom: 20px;
}

.input-label {
  display: block;
  margin-bottom: 8px;
  color: #475569;
  font-weight: 500;
  font-size: 14px;
}

.rounded-input {
  :deep(.el-input__wrapper) {
    border-radius: 12px !important;
    border: 1px solid #e2e8f0 !important;
    background: #f8fafc !important;
    box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.05) !important;
    transition: all 0.3s ease !important;

    &:hover {
      border-color: #bae6fd !important;
    }

    &:focus-within {
      border-color: #0ea5e9 !important;
      box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.1) !important;
    }
  }

  :deep(input) {
    color: #1e293b !important;
  }

  :deep(.el-input__prefix-inner) {
    color: #94a3b8 !important;
  }
}

.input-icon {
  width: 16px;
  height: 16px;
  color: #94a3b8 !important;
}

.captcha-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-box {
  flex-shrink: 0;
  width: 100px;
  height: 46px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: all 0.3s ease;

  &:hover {
    border-color: #0ea5e9;
    box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.1);
  }
}

.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.submit-button {
  width: 100%;
  height: 48px;
  border-radius: 12px !important;
  background: linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%) !important;
  border: none !important;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease !important;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 25px rgba(2, 132, 199, 0.3) !important;
  }

  &:active {
    transform: translateY(0);
  }

  :deep(span) {
    color: white !important;
  }

  :deep(.el-loading-spinner) {
    .circular {
      width: 20px;
      height: 20px;
    }

    .path {
      stroke: white;
    }
  }
}

.login-link {
  text-align: center;
  color: #64748b;
  font-size: 14px;

  .signin-link {
    color: #0ea5e9;
    text-decoration: none;
    font-weight: 600;
    margin-left: 6px;
    transition: color 0.3s ease;

    &:hover {
      color: #0284c7;
      text-decoration: underline;
    }
  }
}

.footer {
  position: relative;
  z-index: 2;
  text-align: center;
  padding: 20px;
  color: #94a3b8;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(5px);
}
</style>
