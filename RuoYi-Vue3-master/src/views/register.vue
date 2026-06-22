<template>
  <div class="auth-page auth-page--register">
    <AuthScene
      mode="register"
      :is-typing="isTyping"
      :password-visible="passwordVisible"
      :password-length="registerForm.password ? registerForm.password.length : 0"
    />

    <main class="auth-panel">
      <section class="auth-form-shell" aria-label="创建账户">
        <div class="auth-heading">
          <p>Create access</p>
          <h1>创建账户</h1>
          <span>填写基础账号信息，注册后由平台统一分配业务权限。</span>
        </div>

        <el-form ref="registerRef" :model="registerForm" :rules="registerRules" class="auth-form" @keyup.enter="handleRegister">
          <el-form-item prop="username">
            <label class="auth-field-label">用户名</label>
            <el-input
              v-model="registerForm.username"
              type="text"
              size="large"
              placeholder="请输入用户名"
              class="auth-input"
              autocomplete="off"
              @focus="isTyping = true"
              @blur="isTyping = false"
            >
              <template #prefix>
                <svg-icon icon-class="user" class="input-icon" />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <label class="auth-field-label">密码</label>
            <el-input
              v-model="registerForm.password"
              :type="passwordVisible ? 'text' : 'password'"
              size="large"
              placeholder="请输入密码"
              class="auth-input"
              autocomplete="off"
              @focus="isTyping = true"
              @blur="isTyping = false"
            >
              <template #prefix>
                <svg-icon icon-class="password" class="input-icon" />
              </template>
              <template #suffix>
                <button
                  type="button"
                  class="password-toggle"
                  :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                  @mousedown.prevent
                  @click.stop="passwordVisible = !passwordVisible"
                >
                  <svg-icon :icon-class="passwordVisible ? 'eye-open' : 'eye'" />
                </button>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <label class="auth-field-label">确认密码</label>
            <el-input
              v-model="registerForm.confirmPassword"
              :type="passwordVisible ? 'text' : 'password'"
              size="large"
              placeholder="请再次输入密码"
              class="auth-input"
              autocomplete="off"
              @focus="isTyping = true"
              @blur="isTyping = false"
            >
              <template #prefix>
                <svg-icon icon-class="lock" class="input-icon" />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="code" v-if="captchaEnabled">
            <label class="auth-field-label">验证码</label>
            <div class="captcha-row">
              <el-input
                v-model="registerForm.code"
                size="large"
                placeholder="请输入验证码"
                class="auth-input captcha-input"
                autocomplete="off"
                @focus="isTyping = true"
                @blur="isTyping = false"
              >
                <template #prefix>
                  <svg-icon icon-class="validCode" class="input-icon" />
                </template>
              </el-input>
              <button type="button" class="captcha-image" title="点击刷新验证码" @click="getCode">
                <img :src="codeUrl" alt="验证码" />
              </button>
            </div>
          </el-form-item>

          <button
            type="button"
            class="interactive-submit"
            :disabled="loading"
            @click.prevent="handleRegister"
          >
            <span class="submit-label">{{ loading ? '注 册 中...' : '注 册' }}</span>
            <span class="submit-hover">
              {{ loading ? '请稍候' : '创建账户' }}
              <svg-icon icon-class="enter" />
            </span>
          </button>

          <div class="auth-switch">
            已有账户？
            <router-link to="/login">立即登录</router-link>
          </div>
        </el-form>
      </section>

      <footer class="auth-copyright" v-html="footerContent"></footer>
    </main>
  </div>
</template>

<script setup>
import AuthScene from "@/components/AuthScene/index.vue"
import { ElMessageBox } from "element-plus"
import { getCodeImg, register } from "@/api/login"
import defaultSettings from "@/settings"

const footerContent = defaultSettings.footerContent || "Copyright © 2026 华东信息融合平台. All Rights Reserved."
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
const isTyping = ref(false)
const passwordVisible = ref(false)

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
      registerForm.value.uuid = res.uuid
    }
  })
}

getCode()
</script>

<style lang="scss" scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(460px, 46vw) minmax(420px, 1fr);
  overflow: hidden;
  background: #fbfdff;
  color: #172235;
}

.auth-panel {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 32px 28px;
}

.auth-form-shell {
  width: min(420px, 100%);
}

.auth-heading {
  margin-bottom: 34px;
  text-align: center;

  p {
    margin: 0 0 10px;
    color: #2d7ef7;
    font-size: 13px;
    font-weight: 760;
  }

  h1 {
    margin: 0;
    color: #111827;
    font-size: 32px;
    line-height: 1.2;
    font-weight: 780;
  }

  span {
    display: block;
    margin-top: 10px;
    color: #66758a;
    font-size: 14px;
    line-height: 1.7;
  }
}

.auth-form {
  :deep(.el-form-item) {
    display: block;
    margin-bottom: 20px;
  }

  :deep(.el-form-item__content) {
    display: block;
  }
}

.auth-field-label {
  display: block;
  margin-bottom: 8px;
  color: #263446;
  font-size: 14px;
  font-weight: 650;
}

.auth-input {
  width: 100%;

  :deep(.el-input__wrapper) {
    min-height: 48px;
    border-radius: 14px !important;
    background: #ffffff !important;
    box-shadow: 0 0 0 1px #dde5ef inset !important;
    transition: background 0.24s ease, box-shadow 0.24s ease, transform 0.24s ease;

    &:hover {
      box-shadow: 0 0 0 1px #b9cbe0 inset !important;
    }

    &:focus-within {
      transform: translateY(-1px);
      box-shadow: 0 0 0 1px #2d7ef7 inset, 0 0 0 4px rgba(45, 126, 247, 0.12) !important;
    }
  }

  :deep(input) {
    color: #1d2b3d !important;

    &::placeholder {
      color: #a0adbc;
    }
  }
}

.input-icon {
  width: 16px;
  height: 16px;
  color: #8392a5;
}

.password-toggle {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #8392a5;
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;

  &:hover {
    color: #2d7ef7;
    background: rgba(45, 126, 247, 0.08);
  }
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 116px;
  gap: 12px;
  align-items: center;
}

.captcha-image {
  height: 48px;
  padding: 0;
  border: 1px solid #dde5ef;
  border-radius: 14px;
  background: #ffffff;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;

  &:hover {
    border-color: #2d7ef7;
    box-shadow: 0 0 0 4px rgba(45, 126, 247, 0.1);
    transform: translateY(-1px);
  }

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.interactive-submit {
  position: relative;
  width: 100%;
  height: 50px;
  margin-top: 2px;
  border: 0;
  border-radius: 999px;
  overflow: hidden;
  background: #111827;
  color: #ffffff;
  font-size: 16px;
  font-weight: 760;
  cursor: pointer;
  transition: transform 0.24s ease, box-shadow 0.24s ease, opacity 0.24s ease;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 18px 34px rgba(17, 24, 39, 0.22);

    .submit-label {
      opacity: 0;
      transform: translateX(44px);
    }

    .submit-hover {
      opacity: 1;
      transform: translateY(0);
    }
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.72;
  }
}

.submit-label,
.submit-hover {
  position: absolute;
  inset: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.submit-hover {
  opacity: 0;
  transform: translateY(18px);
  background: #2d7ef7;
}

.auth-switch {
  margin-top: 26px;
  text-align: center;
  color: #6c7a8c;
  font-size: 14px;

  a {
    margin-left: 6px;
    color: #1e6fe7;
    font-weight: 650;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.auth-copyright {
  width: min(560px, 100%);
  margin-top: auto;
  padding-top: 42px;
  color: #8b98a8;
  text-align: center;
  font-size: 13px;
  line-height: 1.7;
}

@media (prefers-reduced-motion: reduce) {
  .auth-page *,
  .auth-page *::before,
  .auth-page *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}

@media (max-width: 980px) {
  .auth-page {
    display: block;
    min-height: 100vh;
    overflow-y: auto;
    background:
      linear-gradient(90deg, rgba(45, 126, 247, 0.05) 1px, transparent 1px) 0 0 / 36px 36px,
      linear-gradient(180deg, #f8fbff 0%, #eef5ff 100%);
  }

  .auth-panel {
    min-height: auto;
    padding: 18px 22px 24px;
  }

  .auth-form-shell {
    width: min(420px, 100%);
  }

  .auth-copyright {
    padding-top: 30px;
  }
}

@media (max-width: 520px) {
  .auth-heading {
    margin-bottom: 26px;

    h1 {
      font-size: 28px;
    }
  }

  .captcha-row {
    grid-template-columns: 1fr;
  }

  .captcha-image {
    width: 132px;
  }
}
</style>
