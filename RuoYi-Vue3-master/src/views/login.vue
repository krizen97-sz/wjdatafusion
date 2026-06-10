<template>
  <div class="login-container">
    <div class="login-box">
      <!-- Logo and Title -->
      <div class="login-header">
        <img src="@/assets/logo/platform-logo.svg" alt="华东信息融合平台" class="logo-img" />
        <h1 class="title">{{ title }}</h1>
        <p class="subtitle">聚焦现场、平台运维支撑的一体化管控入口</p>
      </div>

      <!-- Login Form -->
      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" @keyup.enter="handleLogin">
        <h2 class="form-title">用户登录</h2>
        
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            type="text"
            size="large"
            placeholder="请输入账号"
            class="custom-input"
            autocomplete="off"
          >
            <template #prefix>
              <svg-icon icon-class="user" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            show-password
            placeholder="请输入密码"
            class="custom-input"
            autocomplete="off"
          >
            <template #prefix>
              <svg-icon icon-class="password" class="input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="code" v-if="captchaEnabled">
          <div class="captcha-wrapper">
            <el-input
              v-model="loginForm.code"
              size="large"
              placeholder="请输入验证码"
              class="custom-input captcha-input"
               autocomplete="off"
            >
              <template #prefix>
                <svg-icon icon-class="validCode" class="input-icon" />
              </template>
            </el-input>
            <div class="captcha-image" @click="getCode">
              <img :src="codeUrl" alt="验证码"/>
            </div>
          </div>
        </el-form-item>

        <div class="options">
          <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
          <a href="javascript:void(0);" class="forgot-password">忘记密码?</a>
        </div>

        <el-form-item>
          <el-button
            :loading="loading"
            type="primary"
            size="large"
            class="login-button"
            @click.prevent="handleLogin"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登 录 中...</span>
          </el-button>
        </el-form-item>

        <div class="register-section" v-if="register">
          没有账户?
          <router-link to="/register" class="register-link">立即注册</router-link>
        </div>
      </el-form>
    </div>
    
    <!-- Footer -->
    <footer class="footer">
      <p v-html="footerContent"></p>
    </footer>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent || 'Copyright © 2026 华东信息融合平台. All Rights Reserved.'
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({
  username: "",
  password: "",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
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
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

getCode()
getCookie()
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(98, 166, 255, 0.22), transparent 34%),
    radial-gradient(circle at bottom right, rgba(149, 205, 255, 0.2), transparent 30%),
    linear-gradient(160deg, #f4f9ff 0%, #edf5ff 52%, #f9fcff 100%);
}

.login-box {
  width: 436px;
  padding: 42px;
  background: rgba(255, 255, 255, 0.84);
  border-radius: 28px;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(196, 217, 241, 0.9);
  box-shadow: 0 26px 60px rgba(64, 105, 156, 0.16);
  color: #23405d;
  z-index: 1;
}

.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
  
  .logo-img {
    width: 68px;
    height: 68px;
    margin-bottom: 12px;
    border-radius: 18px;
    box-shadow: 0 14px 32px rgba(14, 28, 54, 0.26);
  }
  
  .title {
    font-size: 24px;
    font-weight: 600;
    margin: 0;
    color: #17324f;
  }

  .subtitle {
    margin: 10px 0 0;
    font-size: 13px;
    line-height: 1.6;
    text-align: center;
    color: #6a829d;
  }
}

.form-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 24px;
  color: #16324f;
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  .custom-input {
    :deep(.el-input__wrapper) {
      background: rgba(249, 252, 255, 0.96) !important;
      border-radius: 14px !important;
      box-shadow: 0 0 0 1px #d7e4f2 inset !important;
      transition: background 0.3s, border-color 0.3s, box-shadow 0.3s;
      
      &:hover {
        box-shadow: 0 0 0 1px #bed4ef inset !important;
      }

      &:focus-within {
        background: #ffffff !important;
        box-shadow: 0 0 0 1px #6aa4ef inset, 0 0 0 4px rgba(74, 134, 218, 0.12) !important;
      }
    }
    
    :deep(input) {
      color: #23405d !important;
      &::placeholder {
        color: #9ab0c8;
      }
    }

    :deep(.el-input__password) {
      color: #8ca4bd;
      &:hover {
        color: #2c5887;
      }
    }
  }
  
  .input-icon {
    color: #7a95b2;
    margin-left: 5px;
  }
}

.captcha-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  
  .captcha-input {
    flex-grow: 1;
  }
  
  .captcha-image {
    width: 110px;
    height: 40px;
    cursor: pointer;
    border-radius: 12px;
    overflow: hidden;
    border: 1px solid #dbe7f4;
    
    img {
      width: 100%;
      height: 100%;
    }
  }
}

.options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  font-size: 14px;
  
  :deep(.el-checkbox__label) {
    color: #69819c;
  }
  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
      background-color: #2d7ef7;
      border-color: #2d7ef7;
  }
  
  .forgot-password {
    color: #5f7ea0;
    text-decoration: none;
    &:hover {
      color: #2d6fca;
    }
  }
}

.login-button {
  width: 100%;
  height: 48px;
  border-radius: 14px !important;
  border: none !important;
  background: linear-gradient(135deg, #3d8cff, #79b8ff) !important;
  font-size: 16px;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 30px rgba(61, 140, 255, 0.28);
  }
}

.register-section {
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
  color: #6a829d;
  
  .register-link {
    color: #2d7ef7;
    font-weight: bold;
    text-decoration: none;
    margin-left: 5px;
    
    &:hover {
      color: #215fae;
    }
  }
}

.footer {
  position: absolute;
  bottom: 20px;
  left: 0;
  right: 0;
  text-align: center;
  color: #7790aa;
  font-size: 14px;
  z-index: 1;
}

@media (max-width: 640px) {
  .login-box {
    width: min(92vw, 436px);
    padding: 30px 22px;
    border-radius: 22px;
  }

  .footer {
    bottom: 12px;
    padding: 0 16px;
    font-size: 12px;
  }
}
</style>
