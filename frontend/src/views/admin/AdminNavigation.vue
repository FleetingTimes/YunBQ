<template>
  <div class="admin-navigation">
    <!-- 页面标题和操作按钮 -->
    <div class="header">
      <h2>导航管理</h2>
      <el-button type="primary" @click="showAddDialog = true">
        <el-icon><Plus /></el-icon>
        添加导航分类
      </el-button>
    </div>

    <!-- 导航分类列表 -->
    <div class="navigation-list">
      <el-table 
        :data="navigationList" 
        v-loading="loading"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="icon" label="图标" width="100">
          <!-- 说明：后端返回的 icon 是 Font Awesome 类名（如："fas fa-code"、"fab fa-github"）
               不能作为组件名使用，需通过 <i> 标签 + class 来渲染。
               同时在 index.html 中引入 Font Awesome CSS 才能正常显示。 -->
          <template #default="{ row }">
            <i v-if="row.icon" :class="['fa-fw', row.icon]" style="font-size:20px;"></i>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <!-- 状态字段改为 isEnabled，与后端字段一致 -->
        <el-table-column prop="isEnabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled ? 'success' : 'danger'">
              {{ row.isEnabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editNavigation(row)">编辑</el-button>
            <el-button 
              size="small" 
              :type="row.isEnabled ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.isEnabled ? '禁用' : '启用' }}
            </el-button>
            <el-button 
              size="small" 
              type="danger" 
              @click="deleteNavigation(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="showAddDialog"
      :title="editingNavigation ? '编辑导航分类' : '添加导航分类'"
      width="500px"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="navigationForm"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="navigationForm.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="navigationForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入分类描述"
          />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <!-- 说明：此处填写 Font Awesome 的类名，例如："fas fa-code" 或 "fab fa-github"。 -->
          <el-input v-model="navigationForm.icon" placeholder="请输入 Font Awesome 类名（如：fas fa-code）" />
          <!-- 小预览：当填写了类名时，实时展示图标效果，便于校验输入是否正确。 -->
          <div v-if="navigationForm.icon" style="margin-top:8px;">
            <i :class="['fa-fw', navigationForm.icon]" style="font-size:22px;"></i>
            <span style="margin-left:8px; color:#909399;">图标预览</span>
          </div>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number
            v-model="navigationForm.sortOrder"
            :min="0"
            :max="999"
            placeholder="排序值"
          />
        </el-form-item>
        <!-- 使用 isEnabled 与后端保持一致 -->
        <el-form-item label="状态" prop="isEnabled">
          <el-switch
            v-model="navigationForm.isEnabled"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showAddDialog = false">取消</el-button>
          <el-button type="primary" @click="saveNavigation" :loading="saving">
            {{ editingNavigation ? '更新' : '添加' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { http } from '@/api/http'

// 响应式数据
const loading = ref(false)
const saving = ref(false)
const navigationList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const showAddDialog = ref(false)
const editingNavigation = ref(null)
const formRef = ref()

// 表单数据
const navigationForm = reactive({
  name: '',
  description: '',
  icon: '',
  sortOrder: 0,
  // 与后端模型字段一致：isEnabled
  isEnabled: true
})

// 表单验证规则
const formRules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 1, max: 50, message: '长度在 1 到 50 个字符', trigger: 'blur' }
  ],
  description: [
    { max: 200, message: '描述不能超过 200 个字符', trigger: 'blur' }
  ],
  sortOrder: [
    { required: true, message: '请输入排序值', trigger: 'blur' }
  ]
}

// 定义 props 用于更新父组件的统计信息
const props = defineProps({
  updateSummary: Function
})

// 获取导航分类列表
const fetchNavigationList = async () => {
  loading.value = true
  try {
    // 检查token
    const token = localStorage.getItem('token') || sessionStorage.getItem('token')
    console.log('当前token:', token ? '存在' : '不存在')
    console.log('开始获取导航数据...')
    
    // 调整为后端真实接口：/api/navigation/admin/categories
    // 后端分页从1开始，返回 PageResult{ items, total, page, size }
    const response = await http.get('/navigation/admin/categories', {
      params: {
        page: currentPage.value,
        size: pageSize.value
      }
    })

    if (response.data) {
      // 详细说明：后端开启了 Jackson 的 SNAKE_CASE 命名策略（application.yml 中 property-naming-strategy: SNAKE_CASE）
      // 这意味着返回的 JSON 字段会是下划线风格，例如：sort_order、is_enabled、created_at。
      // 为了在前端保持一致的 camelCase 使用（sortOrder、isEnabled、createdAt），此处进行一次字段映射。
      // 注意：未受影响的字段（如 id、name、description、icon）仍为原名，可直接使用。
      console.log('收到的导航数据:', response.data)
      const rawItems = Array.isArray(response.data.items) ? response.data.items : []

      // 将后端 SNAKE_CASE 转换为前端 camelCase，避免 el-table 的 prop 对不上导致不显示
      navigationList.value = rawItems.map(it => ({
        // 基础字段：保持原样
        id: it.id,
        name: it.name,
        description: it.description,
        icon: it.icon,
        // 统一字段命名：优先使用后端 SNAKE_CASE，回退到可能存在的 camelCase（兼容不同来源）
        sortOrder: it.sort_order ?? it.sortOrder ?? 0,
        isEnabled: it.is_enabled ?? it.isEnabled ?? false,
        createdAt: it.created_at ?? it.createdAt ?? null,
        updatedAt: it.updated_at ?? it.updatedAt ?? null,
        parentId: it.parent_id ?? it.parentId ?? null
      }))

      // 分页总数
      total.value = Number(response.data.total || 0)

      // 调试输出：首项的原始字段与映射后的字段，方便定位不渲染问题
      if (rawItems.length) {
        console.log('原始首项字段:', rawItems[0])
        console.log('映射后首项字段:', navigationList.value[0])
      }

      // 更新父组件统计信息（若存在）
      if (props.updateSummary) {
        props.updateSummary({ total: total.value })
      }
    }
  } catch (error) {
    console.error('获取导航分类列表失败:', error)
    ElMessage.error('获取导航分类列表失败')
  } finally {
    loading.value = false
  }
}

// 分页处理
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  fetchNavigationList()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchNavigationList()
}

// 编辑导航分类
const editNavigation = (row) => {
  editingNavigation.value = row
  Object.assign(navigationForm, {
    name: row.name,
    description: row.description || '',
    icon: row.icon || '',
    sortOrder: row.sortOrder || 0,
    // 使用后端字段 isEnabled
    isEnabled: row.isEnabled
  })
  showAddDialog.value = true
}

// 切换状态
const toggleStatus = async (row) => {
  try {
    // 调整为后端真实接口：PATCH /api/navigation/admin/categories/{id}/toggle
    await http.patch(`/navigation/admin/categories/${row.id}/toggle`)
    ElMessage.success(`${row.isEnabled ? '禁用' : '启用'}成功`)
    fetchNavigationList()
  } catch (error) {
    console.error('切换状态失败:', error)
    ElMessage.error('操作失败')
  }
}

// 删除导航分类
const deleteNavigation = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除导航分类"${row.name}"吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 调整为后端真实接口：DELETE /api/navigation/admin/categories/{id}
    await http.delete(`/navigation/admin/categories/${row.id}`)
    ElMessage.success('删除成功')
    fetchNavigationList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 保存导航分类
const saveNavigation = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    saving.value = true
    
    const data = { ...navigationForm }
    
    if (editingNavigation.value) {
      // 更新
      // 调整为后端真实接口：PUT /api/navigation/admin/categories/{id}
      await http.put(`/navigation/admin/categories/${editingNavigation.value.id}`, data)
      ElMessage.success('更新成功')
    } else {
      // 添加
      // 调整为后端真实接口：POST /api/navigation/admin/categories
      await http.post('/navigation/admin/categories', data)
      ElMessage.success('添加成功')
    }
    
    showAddDialog.value = false
    fetchNavigationList()
  } catch (error) {
    if (error.message) {
      console.error('保存失败:', error)
      ElMessage.error('保存失败')
    }
  } finally {
    saving.value = false
  }
}

// 重置表单
const resetForm = () => {
  editingNavigation.value = null
  Object.assign(navigationForm, {
    name: '',
    description: '',
    icon: '',
    sortOrder: 0,
    // 默认启用
    isEnabled: true
  })
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleString('zh-CN')
}

// 组件挂载时获取数据
onMounted(() => {
  fetchNavigationList()
})
</script>

<style scoped>
.admin-navigation {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
  color: #303133;
}

.navigation-list {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-navigation {
    padding: 10px;
  }
  
  .header {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }
  
  .navigation-list {
    padding: 10px;
  }
}
</style>