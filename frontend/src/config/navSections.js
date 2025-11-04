// 公共侧边栏导航配置（广场页 / 添加便签页复用）
// 说明：
// - 该配置与广场页的侧边栏保持一致，便于在多个页面复用相同的父/子导航结构；
// - 使用 id 作为唯一标识；当需要滚动锚点时（仅广场页），父组件可根据 id 进行定位；
// - 对于合并项（如“热门·最近”），通过 aliasTargets 指定多个锚点别名，以便滚动与高亮逻辑处理。

export const sideNavSections = [
  // 合并项：热门·最近（别名锚点映射至 hot 与 recent）
  // 注释：已隐藏"热门·最近"内容区块，同步移除侧边导航入口以避免点击无效果
  // { id: 'hot-recent', label: '热门·最近', aliasTargets: ['hot','recent'] },
// 聚合拾言（网站/站点类聚合）
{ id: 'site', label: '聚合拾言' },
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
  { id: 'git', label: 'git集', aliasTargets: ['git-media','git-tool','git-proxy'], children: [
    { id: 'git-media', label: 'git影音' },
    { id: 'git-tool', label: 'git工具' },
    // 新增：git代理子导航，用于展示git代理相关工具与配置
    { id: 'git-proxy', label: 'git代理' }
  ] },
// 影视拾言：子导航包含在线影视 / 影视软件 / 短视频 / 短视频下载 / 在线动漫
  // 修改：展示文案改为“影视集”
  { id: 'movie', label: '影视集', aliasTargets: ['movie-online'], children: [
    { id: 'movie-online', label: '在线影视' },
    { id: 'movie-software', label: '影视软件' },
    { id: 'movie-short', label: '短视频' },
    { id: 'movie-download', label: '短视频下载' },
    { id: 'movie-anime', label: '在线动漫' }
  ] },
// 音乐拾言：子导航包含在线音乐 / 音乐下载
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
    { id: 'book-download', label: '图书下载' },
    // 新增子导航：图书搜索
    // 说明：
    // - 子项 id 为 'book-search'，与内容区锚点保持一致；
    // - 文案为“图书搜索”，用于承载图书检索与聚合入口相关便签；
    // - 父项 aliasTargets 仍指向首子项（book-online），点击父项滚动到“在线图书”。
    { id: 'book-search', label: '图书搜索' }
  ] },
// 工具拾言
  // 修改：将“工具便签”改为“工具集”，并新增三个子导航
  // 说明：
  // - 父项 id 保持为 'tool'，用于滚动到工具概览区块；
  // - 子项分别为文件工具/影音工具/磁力工具/其他工具，对应内容区锚点：tool-file、tool-media、tool-magnet、tool-other；
  // - 这样点击侧边子导航时可滚动定位到具体分类卡片。
  { id: 'tool', label: '工具集', aliasTargets: ['tool-file'], children: [
    { id: 'tool-file', label: '文件工具' },
    { id: 'tool-media', label: '影音工具' },
    // 新增子导航：磁力工具
    // 说明：
    // - 子项 id 为 'tool-magnet'，用于与内容区锚点一致；
    // - 展示文案为“磁力工具”，对应内容卡片的标签筛选也为“磁力工具”。
    { id: 'tool-magnet', label: '磁力工具' },
    // 新增子导航：插件工具（按需扩展第三方/浏览器插件类工具）
    // 说明：
    // - 子项 id 为 'tool-plugin'，与内容区锚点保持一致（例如 SquareBody 中的 #tool-plugin 卡片）；
    // - 仅新增导航项，不影响其他分类滚动与高亮逻辑。
    { id: 'tool-plugin', label: '插件工具' },
    { id: 'tool-other', label: '其他工具' }
  ] },
// AI 集：将原“AI拾言”改为“AI集”，并将子导航“AI·绘图”改为“AI绘图”
  // 说明：
  // - 仅更新展示文案，不改动 id（仍为 ai / ai-draw），以保证锚点与滚动定位稳定；
  // - 与广场页内容区标题保持一致，避免用户在侧边导航与内容区看到不同文案。
  // AI 集：新增语音/视频/工具子导航
  // 说明：
  // - 保持父项 id 为 'ai'，并保留 aliasTargets 指向首子项以保证点击父项时滚动定位稳定；
  // - 子项 id 与内容区锚点一致（如 #ai-voice/#ai-video/#ai-tool），有助于右侧滚动联动高亮；
  // - 如内容区暂未创建对应卡片，点击子项仅变更高亮，不会触发滚动错误。
  { id: 'ai', label: 'AI集', aliasTargets: ['ai-draw'], children: [ 
    { id: 'ai-draw', label: 'AI绘图' },
    { id: 'ai-voice', label: 'AI语音' },
    { id: 'ai-video', label: 'AI视频' },
    { id: 'ai-tool', label: 'AI工具' }
  ] },
]