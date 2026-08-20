<script setup lang="ts">
import { computed } from 'vue'
import { Search, X } from '@lucide/vue'

const props = defineProps<{
  open: boolean
  loading: boolean
  productQuery: {
    productCode: string
    productName: string
  }
  products: Record<string, unknown>[]
}>()

const emit = defineEmits<{
  (event: 'update:open', value: boolean): void
  (event: 'search'): void
  (event: 'select', row: Record<string, unknown>): void
}>()

const isOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

function closeDialog() {
  isOpen.value = false
}

function priceText(value: unknown) {
  return value ? `¥ ${Number(value).toFixed(2)}` : '-'
}
</script>

<template>
  <div v-if="isOpen" class="attachment-preview-mask" @click.self="closeDialog">
    <section class="supplier-dialog product-selector-dialog" role="dialog" aria-modal="true">
      <header>
        <div>
          <p>定数包模板维护</p>
          <h3>选择商品</h3>
        </div>
        <button class="btn-icon" type="button" aria-label="关闭" @click="closeDialog">
          <X :size="18" />
        </button>
      </header>
      <div class="product-selector-body">
        <div class="product-selector-search">
          <input v-model="productQuery.productCode" placeholder="商品编码" @keyup.enter="emit('search')" />
          <input v-model="productQuery.productName" placeholder="商品名称" @keyup.enter="emit('search')" />
          <button class="btn btn-primary" type="button" @click="emit('search')">
            <Search :size="16" />
            查询
          </button>
        </div>
        <div class="table-scroll">
          <table class="master-table compact-table">
            <thead>
              <tr>
                <th>商品编码</th>
                <th>商品名称</th>
                <th>规格型号</th>
                <th>厂家</th>
                <th>单价</th>
                <th>单位</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="7" class="approval-empty">正在加载商品列表...</td>
              </tr>
              <tr
                v-for="row in products"
                v-else
                :key="String(row.code)"
                class="clickable-row"
                @dblclick="emit('select', row)"
              >
                <td>{{ row.code }}</td>
                <td><strong>{{ row.name }}</strong></td>
                <td>{{ row.spec }}</td>
                <td>{{ row.manufacturer }}</td>
                <td>{{ priceText(row.price) }}</td>
                <td>{{ row.unit }}</td>
                <td>
                  <button class="btn-text" type="button" @click="emit('select', row)">选择</button>
                </td>
              </tr>
              <tr v-if="!loading && !products.length">
                <td colspan="7" class="approval-empty">没有找到已启用定数管理的商品</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>
