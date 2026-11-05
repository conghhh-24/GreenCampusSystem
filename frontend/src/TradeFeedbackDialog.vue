<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api.js'

const props = defineProps({
  modelValue: Boolean,
  trade: Object,
  buyerId: [Number, String]
})
const emit = defineEmits(['update:modelValue', 'submitted', 'refresh'])
const rating = ref(0), comment = ref(''), submitting = ref(false)
const readOnly = computed(() => props.trade?.feedback_submitted === true || props.trade?.status === 'REVIEWED')
const canSubmit = computed(() => props.trade?.can_feedback === true
  && Number(props.trade.buyer_id) === Number(props.buyerId) && !readOnly.value)

watch(() => [props.modelValue, props.trade], () => {
  if (!props.modelValue) return
  rating.value = Number(props.trade?.feedback_rating || 0)
  comment.value = props.trade?.feedback_comment || ''
})

const close = () => { if (!submitting.value) emit('update:modelValue', false) }
const submit = async () => {
  if (submitting.value || !canSubmit.value) return
  if (!Number.isInteger(rating.value) || rating.value < 1 || rating.value > 5) {
    ElMessage.warning('请先选择1—5星评分')
    return
  }
  const tradeId = props.trade.id, score = rating.value, text = comment.value.trim()
  submitting.value = true
  try {
    const result = await api(`/api/trades/${tradeId}/feedback`, {
      method: 'POST', body: JSON.stringify({ buyerId: props.buyerId, rating: score, comment: text })
    })
    emit('update:modelValue', false)
    emit('submitted', result.trade)
    ElMessage.success('评价已提交')
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 409) {
      emit('update:modelValue', false)
      emit('refresh')
    }
  }
  finally { submitting.value = false }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="readOnly ? '交易评价（已评价）' : '评价交易'"
    width="min(520px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!submitting"
    :show-close="!submitting" @update:model-value="close">
    <template v-if="trade">
      <p>订单 #{{ trade.id }} · {{ trade.item_name }}</p>
      <el-form label-position="top" :disabled="submitting">
        <el-form-item label="交易评分" :required="!readOnly">
          <el-rate v-model="rating" :disabled="readOnly" show-text
            :texts="['很不满意', '不满意', '一般', '满意', '很满意']" />
        </el-form-item>
        <el-form-item label="评价内容（选填）">
          <p v-if="readOnly" class="feedback-comment">{{ comment || '未填写评价内容' }}</p>
          <el-input v-else v-model="comment" type="textarea" :rows="4" maxlength="255"
            show-word-limit placeholder="说说物品情况和交易体验" />
        </el-form-item>
      </el-form>
      <p v-if="!readOnly" class="el-upload__tip">每笔已完成交易仅可评价一次，提交后不可修改。</p>
    </template>
    <template #footer>
      <el-button :disabled="submitting" @click="close">{{ readOnly ? '关闭' : '取消' }}</el-button>
      <el-button v-if="canSubmit" type="primary" :loading="submitting" @click="submit">提交评价</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.feedback-comment { white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; }
</style>
