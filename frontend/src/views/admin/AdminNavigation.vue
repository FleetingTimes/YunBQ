<template>
  <div class="admin-navigation">
    <!-- 页面标题和操作按钮 -->
    <div class="header">
      <h2>导航管理</h2>
      <el-button type="primary" @click="openAddDialog">
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
        <el-table-column prop="name" label="分类名称" min-width="150">
          <!-- 为二级分类添加缩进显示，增强层级视觉效果 -->
          <template #default="{ row }">
            <span v-if="row.parentId" style="margin-left: 20px; color: #909399;">
              └─ {{ row.name }}
            </span>
            <span v-else style="font-weight: 600;">
              {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="parentId" label="父级分类" width="120">
          <!-- 显示父级分类名称，如果是一级分类则显示"-" -->
          <template #default="{ row }">
            <span v-if="row.parentId">
              {{ getCategoryName(row.parentId) }}
            </span>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
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
        <!-- 父级分类选择（创建二级分类时选择父级；留空为一级）。
             说明：选择父级后，当前分类将作为其子分类，即二级导航。 -->
        <el-form-item label="父级分类" prop="parentId">
          <el-select
            v-model="navigationForm.parentId"
            placeholder="请选择父级分类（留空为一级）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option :value="null" label="无（一级分类）" />
            <el-option
              v-for="cat in rootCategories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
          <div class="form-tip">选择父级分类可创建二级导航；不选择则创建一级。</div>
        </el-form-item>
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
          <el-input v-model="navigationForm.icon" placeholder="请输入图标类名或URL" />
          <!-- 图标预览：根据输入值类型显示不同预览 -->
          <div v-if="navigationForm.icon" class="icon-preview">
            <i v-if="isClassIcon(navigationForm.icon)" :class="navigationForm.icon"></i>
            <img v-else :src="navigationForm.icon" alt="图标预览" style="width: 20px; height: 20px;" />
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
// 引入通用图标选择组件
// 撤回：不再使用图标选择器组件
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
// 根分类列表：父级选择的数据源（仅 parentId 为 null 的分类）
const rootCategories = ref([])
// 分类ID到名称的映射，用于显示父级分类名称
const categoryNameMap = ref(new Map())

// 表单数据
const navigationForm = reactive({
  // 父级分类ID；null 为一级分类；设置为根分类 id 表示二级
  parentId: null,
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
      // 后端现在返回 camelCase 格式的字段名，与前端保持一致
      // MyBatis 的 map-underscore-to-camel-case: true 配置会自动将数据库的下划线字段转换为驼峰格式
      // Jackson 使用默认的 camelCase 命名策略，确保 API 返回的字段名与前端期望一致
      console.log('收到的导航数据:', response.data)
      const rawItems = Array.isArray(response.data.items) ? response.data.items : []

      // 直接使用后端返回的 camelCase 字段，无需手动映射
      navigationList.value = rawItems

      // 分页总数
      total.value = Number(response.data.total || 0)

      // 构建分类ID到名称的映射，用于显示父级分类名称
      const nameMap = new Map()
      navigationList.value.forEach(category => {
        nameMap.set(category.id, category.name)
      })
      categoryNameMap.value = nameMap

      // 调试输出：首项的原始字段与映射后的字段，方便定位不渲染问题
      if (rawItems.length) {
        console.log('原始首项字段:', rawItems[0])
        console.log('映射后首项字段:', navigationList.value[0])
        console.log('分类名称映射:', Object.fromEntries(categoryNameMap.value))
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

// 获取根分类列表用于父级选择（管理员接口拉取全部并筛选 parentId 为 null）
const fetchRootCategories = async () => {
  try {
    const resp = await http.get('/navigation/admin/categories', {
      params: { page: 1, size: 1000 }
    })
    const items = Array.isArray(resp.data?.items) ? resp.data.items : []
    // 直接使用后端返回的 camelCase 字段，无需手动映射
    rootCategories.value = items.filter(c => c.parentId == null)
  } catch (e) {
    console.error('获取父级分类失败:', e)
    rootCategories.value = []
  }
}

// 根据分类ID获取分类名称
const getCategoryName = (categoryId) => {
  return categoryNameMap.value.get(categoryId) || '未知分类'
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
    parentId: row.parentId ?? null,
    name: row.name,
    description: row.description || '',
    icon: row.icon || '',
    sortOrder: row.sortOrder || 0,
    // 使用后端字段 isEnabled
    isEnabled: row.isEnabled
  })
  showAddDialog.value = true
  // 编辑弹窗打开时刷新父级选项，确保最新数据可选
  fetchRootCategories()
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
    
    // 构造请求体：统一使用 camelCase，与后端当前 JSON 命名保持一致
    // 注意：此前为了兼容 SNAKE_CASE，曾将 parentId 转换为 parent_id 并删除 parentId，
    // 这会导致后端无法绑定到实体的 parentId 字段，从而父ID未入库。这里统一改为 camelCase。
    const data = { ...navigationForm }

    // 类型规范化：确保各字段类型与后端期望一致，避免 UI 输入造成字符串/空值问题
    // 1) parentId：允许为 null（一级分类）；否则确保为数字类型
    if (data.parentId === '' || data.parentId === undefined) {
      data.parentId = null
    } else if (data.parentId !== null) {
      // 一些选择器可能返回字符串，这里显式转换为数字
      const pid = Number(data.parentId)
      data.parentId = Number.isNaN(pid) ? null : pid
    }

    // 2) sortOrder：确保为数字
    data.sortOrder = Number(data.sortOrder || 0)

    // 3) isEnabled：确保为布尔值
    data.isEnabled = Boolean(data.isEnabled)

    // 防御性校验：编辑或新增二级分类时必须选择父级；一级分类允许为 null
    // 这里仅在显式选择了“无（一级分类）”之外的空值时进行提示，避免误触发
    if (editingNavigation.value && editingNavigation.value.id == null) {
      // 新增模式下，如果用户留空但预期添加二级分类，可在后端进行更严格校验
      // 这里不强制拦截，以保留新增一级分类的能力
    }

    // 调试日志：打印即将发送的表单数据
    console.log('准备保存的分类数据:', data)
    console.log('parentId 值:', data.parentId)
    console.log('parentId 类型:', typeof data.parentId)
    console.log('最终发送的数据:', data)
    
    if (editingNavigation.value) {
      // 更新
      // 调整为后端真实接口：PUT /api/navigation/admin/categories/{id}
      console.log('编辑模式，ID:', editingNavigation.value.id)
      const result = await http.put(`/navigation/admin/categories/${editingNavigation.value.id}`, data)
      console.log('更新分类返回结果:', result)
      ElMessage.success('更新成功')
    } else {
      // 添加
      // 调整为后端真实接口：POST /api/navigation/admin/categories
      console.log('新增模式，调用 POST 接口')
      const result = await http.post('/navigation/admin/categories', data)
      console.log('创建分类返回结果:', result)
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
    parentId: null,
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

// 打开新增弹窗：同时刷新父级分类选项
const openAddDialog = () => {
  showAddDialog.value = true
  fetchRootCategories()
}
// 工具函数：判断图标值是否为类名（Font Awesome 等）
// 保留原预览分支使用：类名包含 fa 系列标识，否则视为图片URL
const isClassIcon = (val) => {
  if (!val) return false
  return /\bfa[srb]?\b/.test(val) || /\bfa-/.test(val)
}



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