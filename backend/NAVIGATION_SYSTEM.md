# 导航系统设计文档

## 概述
本系统实现了一个支持二级分类的导航管理系统，用于侧边栏导航和站点管理。

## 数据库表结构

### 1. navigation_categories (导航分类表)
支持二级分类的导航分类管理表。

**字段说明：**
- `id`: 分类ID (主键，自增)
- `parent_id`: 父级分类ID，NULL表示一级分类
- `name`: 分类名称
- `icon`: 分类图标 (CSS类名或图标路径)
- `description`: 分类描述
- `sort_order`: 排序权重，数值越小越靠前
- `is_enabled`: 是否启用
- `created_at`: 创建时间
- `updated_at`: 更新时间

**索引：**
- `idx_parent_id`: 父级分类索引
- `idx_sort_order`: 排序索引
- `idx_enabled`: 启用状态索引

**约束：**
- 自关联外键：`parent_id` 引用 `navigation_categories(id)`

### 2. navigation_sites (导航站点表)
存储导航分类下的具体站点信息。

**字段说明：**
- `id`: 站点ID (主键，自增)
- `category_id`: 所属分类ID
- `name`: 站点名称
- `url`: 站点URL
- `description`: 站点描述
- `icon`: 站点图标 (CSS类名或图标路径)
- `favicon_url`: 站点favicon地址
- `tags`: 标签，逗号分隔
- `sort_order`: 排序权重，数值越小越靠前
- `is_enabled`: 是否启用
- `is_featured`: 是否为推荐站点
- `click_count`: 点击次数统计
- `user_id`: 添加用户ID (用户自定义站点)
- `created_at`: 创建时间
- `updated_at`: 更新时间

**索引：**
- `idx_category_id`: 分类索引
- `idx_sort_order`: 排序索引
- `idx_enabled`: 启用状态索引
- `idx_featured`: 推荐状态索引
- `idx_click_count`: 点击次数索引
- `idx_user_id`: 用户索引
- `idx_tags`: 标签索引

**约束：**
- 外键：`category_id` 引用 `navigation_categories(id)`
- 外键：`user_id` 引用 `users(id)`

## Java实体类

### 1. NavigationCategory.java
导航分类实体类，支持二级分类结构。

**主要注解：**
- `@TableName("navigation_categories")`: 指定表名
- `@TableId(type = IdType.AUTO)`: 主键自增
- `@Data`: Lombok注解，自动生成getter/setter

### 2. NavigationSite.java
导航站点实体类。

**主要注解：**
- `@TableName("navigation_sites")`: 指定表名
- `@TableId(type = IdType.AUTO)`: 主键自增
- `@Data`: Lombok注解，自动生成getter/setter

## Mapper接口

### 1. NavigationCategoryMapper.java
导航分类数据访问接口。

**主要方法：**
- `findAllEnabledRootCategories()`: 查询所有启用的一级分类
- `findSubCategoriesByParentId(Long parentId)`: 根据父级ID查询子分类
- `findAllEnabledCategories()`: 查询所有启用的分类

### 2. NavigationSiteMapper.java
导航站点数据访问接口。

**主要方法：**
- `findSitesByCategoryId(Long categoryId)`: 根据分类ID查询站点
- `findFeaturedSites()`: 查询推荐站点
- `findPopularSites(int limit)`: 查询热门站点
- `searchSitesByTags(String tags)`: 根据标签搜索站点
- `incrementClickCount(Long siteId)`: 增加站点点击次数
- `findSitesByUserId(Long userId)`: 查询用户添加的站点

## 示例数据

系统已预置了以下分类和站点数据：

**一级分类：**
1. Development Tools (开发工具)
2. Design Resources (设计资源)
3. Learning & Education (学习教育)
4. Life Services (生活服务)
5. Entertainment (娱乐休闲)

**二级分类示例：**
- Development Tools
  - Code Hosting (代码托管)
  - Online Editors (在线编辑器)
  - Documentation (开发文档)
- Design Resources
  - Icons (图标素材)
  - Color Schemes (配色方案)
  - Fonts (字体资源)
- Learning & Education
  - Programming Learning (编程学习)
  - Online Courses (在线课程)
  - Tech Blogs (技术博客)

**站点示例：**
- GitHub, GitLab, Bitbucket (代码托管)
- CodePen, JSFiddle, CodeSandbox (在线编辑器)
- Font Awesome, Feather Icons, Heroicons (图标素材)
- Coolors, Adobe Color (配色方案)
- MDN Web Docs, W3Schools, Stack Overflow (编程学习)

## 使用说明

1. **分类管理**：支持二级分类，可以灵活组织导航结构
2. **站点管理**：每个站点可以关联到具体的分类，支持排序和启用/禁用
3. **用户自定义**：支持用户添加自定义站点
4. **统计功能**：记录站点点击次数，可用于热门站点推荐
5. **标签系统**：支持标签搜索，便于站点发现
6. **推荐机制**：支持设置推荐站点，可在首页或特殊位置展示

## 扩展建议

1. **缓存优化**：对于频繁查询的分类和热门站点，可以添加Redis缓存
2. **搜索功能**：可以集成Elasticsearch实现全文搜索
3. **个性化推荐**：基于用户行为数据实现个性化站点推荐
4. **导入导出**：支持书签导入导出功能
5. **API接口**：提供RESTful API供前端调用
6. **权限控制**：可以添加分类和站点的访问权限控制