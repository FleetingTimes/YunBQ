// 公共侧边栏导航配置（广场页 / 添加便签页复用）
// 说明：
// - 该配置与广场页的侧边栏保持一致，便于在多个页面复用相同的父/子导航结构；
// - 使用 id 作为唯一标识；当需要滚动锚点时（仅广场页），父组件可根据 id 进行定位；
// - 对于合并项（如“热门·最近”），通过 aliasTargets 指定多个锚点别名，以便滚动与高亮逻辑处理。

export const sideNavSections = [
  // 合并项：热门·最近（别名锚点映射至 hot 与 recent）
  { id: 'hot-recent', label: '热门·最近', aliasTargets: ['hot','recent'] },
  // 聚合便签（网站/站点类聚合）
  { id: 'site', label: '聚合便签' },
  // Git 便签：子导航包含 git影音 / git工具
  { id: 'git', label: 'git便签', children: [
    { id: 'git-media', label: 'git影音' },
    { id: 'git-tool', label: 'git工具' }
  ] },
  // 知识便签
  { id: 'knowledge', label: '知识便签' },
  // 影视便签：子导航包含在线影视 / 影视软件 / 短视频 / 短视频下载 / 在线动漫
  { id: 'movie', label: '影视便签', children: [
    { id: 'movie-online', label: '在线影视' },
    { id: 'movie-software', label: '影视软件' },
    { id: 'movie-short', label: '短视频' },
    { id: 'movie-download', label: '短视频下载' },
    { id: 'movie-anime', label: '在线动漫' }
  ] },
  // 音乐便签：子导航包含在线音乐 / 音乐下载
  { id: 'music', label: '音乐便签', children: [
    { id: 'music-online', label: '在线音乐' },
    { id: 'music-download', label: '音乐下载' }
  ] },
  // 工具便签
  { id: 'tool', label: '工具便签' },
  // AI 便签：子导航包含 AI·绘图
  { id: 'ai', label: 'AI便签', children: [ { id: 'ai-draw', label: 'AI·绘图' } ] },
]