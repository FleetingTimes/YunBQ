<template>
  <div class="admin-sites">
    <!-- 页面标题和操作按钮 -->
    <div class="header">
      <h2>站点管理</h2>
      <div class="header-actions">
        <el-select
          v-model="selectedCategory"
          placeholder="选择分类筛选"
          clearable
          @change="handleCategoryChange"
          style="width: 200px; margin-right: 10px;"
        >
          <el-option
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
        <el-button type="primary" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon>
          添加站点
        </el-button>
      </div>
    </div>

    <!-- 站点列表 -->
    <div class="sites-list">
      <el-table 
        :data="sitesList" 
        v-loading="loading"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="站点名称" min-width="150" />
        <el-table-column prop="url" label="站点链接" min-width="200">
          <template #default="{ row }">
            <el-link :href="row.url" target="_blank" type="primary">
              {{ row.url }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="categoryName" label="分类" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.categoryName" type="info">{{ row.categoryName }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="80">
          <template #default="{ row }">
            <img 
              v-if="row.icon" 
              :src="row.icon" 
              :alt="row.name"
              style="width: 24px; height: 24px; border-radius: 4px;"
              @error="handleImageError"
            />
            <el-icon v-else :size="24">
              <Link />
            </el-icon>
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
            <el-button size="small" @click="editSite(row)">编辑</el-button>
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
              @click="deleteSite(row)"
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
      :title="editingSite ? '编辑站点' : '添加站点'"
      width="600px"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="siteForm"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="站点名称" prop="name">
          <el-input v-model="siteForm.name" placeholder="请输入站点名称" />
        </el-form-item>
        <el-form-item label="站点链接" prop="url">
          <el-input v-model="siteForm.url" placeholder="请输入站点链接（如：https://example.com）" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="siteForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入站点描述"
          />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="siteForm.categoryId" placeholder="请选择分类" style="width: 100%;">
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="图标链接" prop="icon">
          <el-input v-model="siteForm.icon" placeholder="请输入图标链接（可选）" />
          <div class="icon-preview" v-if="siteForm.icon">
            <img 
              :src="siteForm.icon" 
              alt="图标预览"
              style="width: 32px; height: 32px; border-radius: 4px; margin-top: 8px;"
              @error="handleImageError"
            />
          </div>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number
            v-model="siteForm.sortOrder"
            :min="0"
            :max="999"
            placeholder="排序值"
          />
        </el-form-item>
        <!-- 状态字段：与后端一致使用 isEnabled，避免 isActive/isEnabled 混用导致表单值不生效 -->
        <el-form-item label="状态" prop="isEnabled">
          <el-switch
            v-model="siteForm.isEnabled"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showAddDialog = false">取消</el-button>
          <el-button type="primary" @click="saveSite" :loading="saving">
            {{ editingSite ? '更新' : '添加' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Link } from '@element-plus/icons-vue'
import { http } from '@/api/http'

// 响应式数据
const loading = ref(false)
const saving = ref(false)
const sitesList = ref([])
const categories = ref([])
const selectedCategory = ref('')
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const showAddDialog = ref(false)
const editingSite = ref(null)
const formRef = ref()

// 表单数据
const siteForm = reactive({
  name: '',
  url: '',
  description: '',
  categoryId: '',
  icon: '',
  sortOrder: 0,
  // 与后端模型字段一致：isEnabled
  isEnabled: true
})

// 表单验证规则
const formRules = {
  name: [
    { required: true, message: '请输入站点名称', trigger: 'blur' },
    { min: 1, max: 100, message: '长度在 1 到 100 个字符', trigger: 'blur' }
  ],
  url: [
    { required: true, message: '请输入站点链接', trigger: 'blur' },
    { 
      pattern: /^https?:\/\/.+/, 
      message: '请输入有效的网址（以http://或https://开头）', 
      trigger: 'blur' 
    }
  ],
  description: [
    { max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' }
  ],
  categoryId: [
    { required: true, message: '请选择分类', trigger: 'change' }
  ],
  sortOrder: [
    { required: true, message: '请输入排序值', trigger: 'blur' }
  ]
}

// 定义 props 用于更新父组件的统计信息
const props = defineProps({
  updateSummary: Function
})

// 获取分类列表
const fetchCategories = async () => {
  try {
    // 使用后端公开接口：/api/navigation/categories/all，返回所有启用分类列表
    const response = await http.get('/navigation/categories/all')
    if (response.data) {
      // 返回的列表已是启用的分类
      categories.value = response.data
    }
  } catch (error) {
    console.error('获取分类列表失败:', error)
  }
}

// 获取站点列表
const fetchSitesList = async () => {
  loading.value = true
  try {
    const params = {
      // 后端分页从1开始
      page: currentPage.value,
      size: pageSize.value
    }
    
    // 如果选择了分类，添加分类筛选
    if (selectedCategory.value) {
      params.categoryId = selectedCategory.value
    }
    
    // 调整为后端真实接口：/api/navigation/admin/sites
    const response = await http.get('/navigation/admin/sites', { params })

    if (response.data) {
      // 重要说明：后端开启了 Jackson 的 SNAKE_CASE 命名策略（application.yml 中 property-naming-strategy: SNAKE_CASE）
      // 这会导致返回的 JSON 字段使用下划线风格，例如：category_id、sort_order、is_enabled、created_at、updated_at、click_count。
      // 为了让表格列的 camelCase prop（如 sortOrder、isEnabled、createdAt）正确匹配并显示，这里统一将 SNAKE_CASE 映射为 camelCase。
      // 同时保留部分基础字段（id、name、url、description、icon）原样使用。
      console.log('收到的站点列表响应:', response.data)
      const rawItems = Array.isArray(response.data.items) ? response.data.items : []

      // 字段映射：将 snake_case -> camelCase，并补充 categoryName 便于显示
      // 说明：categoryName 通过已加载的 categories 列表匹配所得，若未匹配到则置为空串。
      sitesList.value = rawItems.map(it => {
        const categoryId = it.category_id ?? it.categoryId ?? null
        const category = categories.value.find(c => c.id === categoryId)
        return {
          // 基础字段（原样）
          id: it.id,
          name: it.name,
          url: it.url,
          description: it.description,
          icon: it.icon,

          // 统一命名（兼容不同来源）：优先使用 SNAKE_CASE，回退到 camelCase
          categoryId,
          categoryName: category ? category.name : '',
          sortOrder: it.sort_order ?? it.sortOrder ?? 0,
          isEnabled: it.is_enabled ?? it.isEnabled ?? false,
          clickCount: it.click_count ?? it.clickCount ?? 0,
          createdAt: it.created_at ?? it.createdAt ?? null,
          updatedAt: it.updated_at ?? it.updatedAt ?? null
        }
      })

      // 调试输出：首项原始与映射后的字段，帮助定位不渲染问题
      if (rawItems.length) {
        console.log('原始首项字段:', rawItems[0])
        console.log('映射后首项字段:', sitesList.value[0])
      }

      total.value = response.data.total || 0
      
      // 更新父组件统计信息
      if (props.updateSummary) {
        props.updateSummary({ total: total.value })
      }
    }
  } catch (error) {
    console.error('获取站点列表失败:', error)
    ElMessage.error('获取站点列表失败')
  } finally {
    loading.value = false
  }
}

// 分类筛选变化处理
const handleCategoryChange = () => {
  currentPage.value = 1
  fetchSitesList()
}

// 分页处理
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  fetchSitesList()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchSitesList()
}

// 编辑站点
const editSite = (row) => {
  editingSite.value = row
  Object.assign(siteForm, {
    name: row.name,
    url: row.url,
    description: row.description || '',
    categoryId: row.categoryId,
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
    // 调整为后端真实接口：PATCH /api/navigation/admin/sites/{id}/toggle
    await http.patch(`/navigation/admin/sites/${row.id}/toggle`)
    ElMessage.success(`${row.isEnabled ? '禁用' : '启用'}成功`)
    fetchSitesList()
  } catch (error) {
    console.error('切换状态失败:', error)
    ElMessage.error('操作失败')
  }
}

// 删除站点
const deleteSite = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除站点"${row.name}"吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 调整为后端真实接口：DELETE /api/navigation/admin/sites/{id}
    await http.delete(`/navigation/admin/sites/${row.id}`)
    ElMessage.success('删除成功')
    fetchSitesList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 保存站点
const saveSite = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    saving.value = true
    
    const data = { ...siteForm }
    
    if (editingSite.value) {
      // 更新
      // 调整为后端真实接口：PUT /api/navigation/admin/sites/{id}
      await http.put(`/navigation/admin/sites/${editingSite.value.id}`, data)
      ElMessage.success('更新成功')
    } else {
      // 添加
      // 调整为后端真实接口：POST /api/navigation/admin/sites
      await http.post('/navigation/admin/sites', data)
      ElMessage.success('添加成功')
    }
    
    showAddDialog.value = false
    fetchSitesList()
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
  editingSite.value = null
  Object.assign(siteForm, {
    name: '',
    url: '',
    description: '',
    categoryId: '',
    icon: '',
    sortOrder: 0,
    // 默认启用
    isEnabled: true
  })
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

// 图片加载错误处理
const handleImageError = (event) => {
  event.target.style.display = 'none'
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleString('zh-CN')
}

// 组件挂载时获取数据
onMounted(() => {
  fetchCategories()
  fetchSitesList()
})
</script>

<style scoped>
.admin-sites {
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

.header-actions {
  display: flex;
  align-items: center;
}

.sites-list {
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

.icon-preview {
  margin-top: 8px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-sites {
    padding: 10px;
  }
  
  .header {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }
  
  .header-actions {
    flex-direction: column;
    gap: 10px;
  }
  
  .sites-list {
    padding: 10px;
  }
}
</style>