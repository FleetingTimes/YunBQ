<template>
  <div class="admin-sites">
    <!-- 页面标题和操作按钮 -->
    <div class="header">
      <h2>站点管理</h2>
      <div class="header-actions">
        <!-- 分类筛选改为级联选择器（Cascader）
             设计说明：
             - 支持一级/二级分类的层级展示与选择；
             - 为避免改变后端筛选语义，仍然按“选中的分类ID”进行过滤；
             - 选中一级分类不会自动包含子分类（保持与原后端参数一致），如需包含子级可后续扩展为传递 includeChildren 参数或在后端实现；
             - 使用 emitPath: false 和 checkStrictly: true，允许选择任意层级并仅返回最后一级值（即分类ID）。
        -->
        <el-cascader
          v-model="filterCascaderValue"
          :options="cascaderOptions"
          :props="cascaderProps"
          placeholder="选择分类筛选（支持层级）"
          clearable
          style="width: 240px; margin-right: 10px;"
          @change="handleFilterCascaderChange"
        />
        <el-button type="primary" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon>
          添加站点
        </el-button>
        <!-- 导出功能：一键导出所有站点信息（CSV/JSON） -->
        <el-dropdown style="margin-left: 10px;">
          <el-button type="success">
            导出全部站点
            <el-icon style="margin-left:4px"><Link /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="exportAll('csv')">导出为 CSV</el-dropdown-item>
              <el-dropdown-item @click="exportAll('json')">导出为 JSON</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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
          <!-- 级联选择器：支持一级分类->二级分类的层级选择 -->
          <!-- 如果选择一级分类，直接使用一级分类ID；如果选择二级分类，使用二级分类ID -->
          <el-cascader
            v-model="cascaderValue"
            :options="cascaderOptions"
            :props="cascaderProps"
            placeholder="请选择分类"
            style="width: 100%;"
            clearable
            @change="handleCascaderChange"
          />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <!-- 文本输入方式：填写 Font Awesome 类名或图片URL，保持与后端字段一致 -->
          <el-input v-model="siteForm.icon" placeholder="请输入图标类名（如：fas fa-code）或图片URL" />
          <!-- 小预览：基于值类型判断显示类名或图片 -->
          <div class="icon-preview" v-if="siteForm.icon" style="margin-top: 8px;">
            <template v-if="isClassIcon(siteForm.icon)">
              <i :class="['fa-fw', siteForm.icon]" style="font-size:22px;"></i>
              <span style="margin-left:8px; color:#909399;">图标预览（类名）</span>
            </template>
            <template v-else>
              <img 
                :src="siteForm.icon" 
                alt="图标预览"
                style="width: 32px; height: 32px; border-radius: 4px;"
                @error="handleImageError"
              />
              <span style="margin-left:8px; color:#909399;">图标预览（图片）</span>
            </template>
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
// 导出 API：封装的导出接口（获取 Blob）
import { exportAllSites } from '@/api/navigation'
// 引入通用图标选择组件，用于图标类名或URL选择
// 撤回：不再使用图标选择器组件，恢复为文本输入

// 响应式数据
const loading = ref(false)
const saving = ref(false)
const sitesList = ref([])
const categories = ref([])
// 顶部列表筛选：选中分类 ID（与后端参数一致）
const selectedCategory = ref('')
// 顶部列表筛选：级联选择器当前路径值（如：[rootId] 或 [rootId, childId]）
const filterCascaderValue = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const showAddDialog = ref(false)
const editingSite = ref(null)
const formRef = ref()

// 级联选择器相关数据
const cascaderValue = ref([])  // 级联选择器的值，数组形式：[一级分类ID] 或 [一级分类ID, 二级分类ID]
const cascaderOptions = ref([])  // 级联选择器的选项数据
const cascaderProps = {
  // 级联选择器配置
  value: 'id',           // 选项的值为 id 字段
  label: 'name',         // 选项的标签为 name 字段  
  children: 'children',  // 子选项的字段名为 children
  emitPath: false,       // 只返回最后一级的值，而不是完整路径
  checkStrictly: true    // 允许选择任意一级
}

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

// 工具函数：判断当前图标值是否为类名（Font Awesome 等）
// 说明：类名通常包含 "fa" 前缀或 "fa-" 子串；URL 通常以 http/https 开头
const isClassIcon = (val) => {
  if (!val) return false
  return /\bfa[srb]?\b/.test(val) || /\bfa-/.test(val)
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
      // 后端现在返回 camelCase 格式的字段名，直接使用即可
      const rawItems = Array.isArray(response.data) ? response.data : []
      categories.value = rawItems
      
      // 构建级联选择器的数据结构
      buildCascaderOptions()
    }
  } catch (error) {
    console.error('获取分类列表失败:', error)
  }
}

// 构建级联选择器的数据结构
const buildCascaderOptions = () => {
  // 分离一级分类和二级分类
  const rootCategories = categories.value.filter(cat => !cat.parent_id && !cat.parentId)
  const subCategories = categories.value.filter(cat => cat.parent_id || cat.parentId)
  
  // 构建级联选择器的选项数据
  cascaderOptions.value = rootCategories.map(rootCat => {
    // 查找该一级分类下的子分类
    const children = subCategories
      .filter(subCat => {
        const parentId = subCat.parent_id || subCat.parentId
        return parentId === rootCat.id
      })
      .map(subCat => ({
        id: subCat.id,
        name: subCat.name,
        // 二级分类没有子级，所以不需要 children 字段
      }))
      .sort((a, b) => {
          // 按 sort_order 排序，从原始分类数据中获取
          const aCat = subCategories.find(cat => cat.id === a.id)
          const bCat = subCategories.find(cat => cat.id === b.id)
          const aOrder = aCat?.sort_order || aCat?.sortOrder || 0
          const bOrder = bCat?.sort_order || bCat?.sortOrder || 0
          return aOrder - bOrder
        })
    
    const option = {
      id: rootCat.id,
      name: rootCat.name
    }
    
    // 如果有子分类，添加 children 字段
    if (children.length > 0) {
      option.children = children
    }
    
    return option
  }).sort((a, b) => {
    // 按 sort_order 排序，从原始分类数据中获取
    const aCat = rootCategories.find(cat => cat.id === a.id)
    const bCat = rootCategories.find(cat => cat.id === b.id)
    const aOrder = aCat?.sort_order || aCat?.sortOrder || 0
    const bOrder = bCat?.sort_order || bCat?.sortOrder || 0
    return aOrder - bOrder
  })
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
      // 后端现在返回 camelCase 格式的字段名，直接使用即可
      // MyBatis 和 Jackson 配置确保了字段名的一致性
      const rawItems = Array.isArray(response.data.items) ? response.data.items : []
      
      // 补充 categoryName 便于显示
      sitesList.value = rawItems.map(it => {
        const category = categories.value.find(c => c.id === it.categoryId)
        return {
          ...it,
          categoryName: category ? category.name : ''
        }
      })

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

// 顶部筛选：级联选择器值变化处理
function handleFilterCascaderChange(value){
  // 说明：
  // - 由于设置 emitPath=false，value 为选中项的“分类ID”（最后一级）；
  // - 为保持与后端筛选语义一致，这里直接用该 ID 作为筛选参数；
  // - 若希望选择一级分类时包含其所有子分类，可扩展为传递 includeChildren 参数并调整后端。
  selectedCategory.value = value || ''
  currentPage.value = 1
  fetchSitesList()
}

// 分页处理
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  fetchSitesList()
}
// 分页当前页变化处理
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
    // 确保与后端一致的字段名
    isEnabled: row.isEnabled
  })
  
  // 设置级联选择器的值
  setCascaderValueByCategoryId(row.categoryId)
  // 顶部筛选级联值也根据当前行分类进行同步，便于编辑后视觉一致（不影响筛选结果）
  setFilterCascaderByCategoryId(row.categoryId)
  
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

// 级联选择器值变化处理
const handleCascaderChange = (value) => {
  // value 是选中的分类ID（因为设置了 emitPath: false）
  // 将选中的分类ID赋值给表单的categoryId字段
  siteForm.categoryId = value || ''
  
  // 同时更新级联选择器的显示值
  if (value) {
    // 根据选中的分类ID构建级联路径
    const category = findCategoryById(value)
    if (category) {
      const parentId = category.parent_id || category.parentId
       if (parentId) {
         // 如果是二级分类，路径为 [一级分类ID, 二级分类ID]
         cascaderValue.value = [parentId, category.id]
      } else {
        // 如果是一级分类，路径为 [一级分类ID]
        cascaderValue.value = [category.id]
      }
    }
  } else {
    cascaderValue.value = []
  }
}

// 一键导出所有站点
// 说明：
// - 调用后端 /navigation/admin/sites/export 接口获取 Blob（二进制）；
// - 根据 format 设置文件名与 MIME 类型；
// - 通过创建临时 <a> 标签触发浏览器下载；
// - 成功后提示，失败时给出错误信息。
const exportAll = async (format = 'csv') => {
  try {
    const resp = await exportAllSites(format)
    const blob = resp?.data
    if (!blob) throw new Error('导出失败：未获取到文件内容')

    // 修复：直接使用后端返回的 Blob，不要二次包装
    // 后端已经设置了正确的 Content-Type，axios responseType: 'blob' 会自动处理
    const filename = format === 'json' ? 'sites.json' : 'sites.csv'
    
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    ElMessage.success('导出成功，正在下载文件…')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error(error?.message || '导出失败')
  }
}

// 根据分类ID查找分类信息（包含父级信息）
const findCategoryById = (categoryId) => {
  return categories.value.find(cat => cat.id === categoryId)
}

// 根据分类ID设置级联选择器的值
const setCascaderValueByCategoryId = (categoryId) => {
  if (!categoryId) {
    cascaderValue.value = []
    return
  }
  
  const category = findCategoryById(categoryId)
  if (category) {
    const parentId = category.parent_id || category.parentId
    if (parentId) {
      // 二级分类：[一级分类ID, 二级分类ID]
      cascaderValue.value = [parentId, categoryId]
    } else {
      // 一级分类：[一级分类ID]
      cascaderValue.value = [categoryId]
    }
  } else {
    cascaderValue.value = []
  }
}

// 根据分类ID设置顶部筛选的级联选择器值（仅同步显示路径，不触发筛选）
const setFilterCascaderByCategoryId = (categoryId) => {
  if (!categoryId){
    filterCascaderValue.value = []
    return
  }
  const category = findCategoryById(categoryId)
  if (category){
    const parentId = category.parent_id || category.parentId
    if (parentId){
      filterCascaderValue.value = [parentId, categoryId]
    } else {
      filterCascaderValue.value = [categoryId]
    }
  } else {
    filterCascaderValue.value = []
  }
}

// 保存站点
const saveSite = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    saving.value = true
    
    // 重要说明：后端现在统一使用 camelCase JSON 字段（已移除 Jackson 的 SNAKE_CASE 策略），
    // 并通过 MyBatis-Plus 的驼峰映射将 camelCase 映射到数据库下划线列名。
    // 因此前端必须直接发送 camelCase 字段，否则会出现实体绑定失败导致的业务校验报错（如“分类ID不能为空”）。
    // 这里构造一个显式的 camelCase 请求载荷，并进行适当的类型规范化：
    // - categoryId/sortOrder：后端期望为数字类型，UI可能产生字符串，需要 Number() 转换
    // - isEnabled：布尔类型，确保为 true/false
    const data = {
      // 直通字段：与后端实体字段同名
      name: siteForm.name,
      url: siteForm.url,
      description: siteForm.description,
      icon: siteForm.icon,
      // 关键字段：确保使用 camelCase，并做类型转换
      categoryId: siteForm.categoryId !== '' && siteForm.categoryId != null
        ? Number(siteForm.categoryId)
        : null,
      sortOrder: siteForm.sortOrder != null
        ? Number(siteForm.sortOrder)
        : 0,
      isEnabled: Boolean(siteForm.isEnabled ?? true)
      // 预留：如后续新增 faviconUrl、isFeatured、tags 等字段，保持 camelCase 直接发送
      // faviconUrl: siteForm.faviconUrl,
      // isFeatured: siteForm.isFeatured,
      // tags: siteForm.tags
    }
    
    // 额外防御：若分类未选择，直接给出提示并阻止提交，避免后端 400
    if (data.categoryId == null || Number.isNaN(data.categoryId)) {
      ElMessage.error('请选择分类')
      saving.value = false
      return
    }
    
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
  // 重置级联选择器的值
  cascaderValue.value = []
  
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
  // 初始化筛选级联路径（若首次加载 selectedCategory 有值）
  if (selectedCategory.value){
    setFilterCascaderByCategoryId(selectedCategory.value)
  }
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