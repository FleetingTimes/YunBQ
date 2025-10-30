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
  // 云盘集：将原“知识便签”改为“云盘集”，并提升至 git 之前
  // 说明：
  // - 仅更新展示文案（label），不改变 id（仍为 'knowledge'），以保持锚点滚动与组件引用稳定；
  // - 调整顺序：将“云盘集”置于“git集”之前，侧边导航优先展示云盘。
  // 云盘集：新增子导航“云盘搜索 / 云盘工具”，并设置父项滚动映射到首子项
  // 说明：
  // - 为父项添加 children，使侧边栏展示树状子导航；
  // - 增加 aliasTargets:['knowledge-search']，点击父项时滚动到首子卡片；
  // - 子项 id 命名遵循约定 <parent>-<child>，便于内容区锚点一致与维护。
  { id: 'knowledge', label: '云盘集', aliasTargets: ['knowledge-search'], children: [
    { id: 'knowledge-search', label: '云盘搜索' },
    { id: 'knowledge-tool', label: '云盘工具' }
  ] },
  // Git 集：将原“git便签”改为“git集”，子导航保持不变
  // 说明：
  // - 仅修改 label 文案，不改变 id 及子项结构；
  // 父导航存在子导航时：右侧内容区不再渲染父卡片，点击父项应滚动到首个子项。
  // 因此为父项增加 aliasTargets，首元素作为滚动目标。
  { id: 'git', label: 'git集', aliasTargets: ['git-media','git-tool'], children: [
    { id: 'git-media', label: 'git影音' },
    { id: 'git-tool', label: 'git工具' }
  ] },
  // 影视便签：子导航包含在线影视 / 影视软件 / 短视频 / 短视频下载 / 在线动漫
  // 修改：展示文案改为“影视集”
  { id: 'movie', label: '影视集', aliasTargets: ['movie-online'], children: [
    { id: 'movie-online', label: '在线影视' },
    { id: 'movie-software', label: '影视软件' },
    { id: 'movie-short', label: '短视频' },
    { id: 'movie-download', label: '短视频下载' },
    { id: 'movie-anime', label: '在线动漫' }
  ] },
  // 音乐便签：子导航包含在线音乐 / 音乐下载
  // 修改：展示文案改为“音乐集”
  { id: 'music', label: '音乐集', aliasTargets: ['music-online'], children: [
    { id: 'music-online', label: '在线音乐' },
    { id: 'music-download', label: '音乐下载' }
  ] },
  // 图书集：子导航包含 在线图书 / 图书下载
  // 说明：
  // - 父项 id 保持为 'book'，用于滚动到图书概览区块；
  // - 子项 id 分别为 'book-online' 与 'book-download'，对应具体分类卡片；
  // - 保持与其他分类一致的结构，便于 SideNav 统一渲染与滚动。
  { id: 'book', label: '图书集', aliasTargets: ['book-online'], children: [
    { id: 'book-online', label: '在线图书' },
    { id: 'book-download', label: '图书下载' }
  ] },
  // 工具便签
  // 修改：将“工具便签”改为“工具集”，并新增三个子导航
  // 说明：
  // - 父项 id 保持为 'tool'，用于滚动到工具概览区块；
  // - 子项分别为文件工具/影音工具/其他工具，对应内容区锚点：tool-file、tool-media、tool-other；
  // - 这样点击侧边子导航时可滚动定位到具体分类卡片。
  { id: 'tool', label: '工具集', aliasTargets: ['tool-file'], children: [
    { id: 'tool-file', label: '文件工具' },
    { id: 'tool-media', label: '影音工具' },
    { id: 'tool-other', label: '其他工具' }
  ] },
  // AI 集：将原“AI便签”改为“AI集”，并将子导航“AI·绘图”改为“AI绘图”
  // 说明：
  // - 仅更新展示文案，不改动 id（仍为 ai / ai-draw），以保证锚点与滚动定位稳定；
  // - 与广场页内容区标题保持一致，避免用户在侧边导航与内容区看到不同文案。
  { id: 'ai', label: 'AI集', aliasTargets: ['ai-draw'], children: [ { id: 'ai-draw', label: 'AI绘图' } ] },
]