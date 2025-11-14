@echo off
title 启动云便签项目（自动打开后端和前端）

:: 第一步：打开后端命令行窗口
echo 启动后端服务中...
start "后端命令行窗口" cmd /k "e: && set path=E:\Java\jdk-17.0.6\bin;%path% && cd Trea_WorkSpace\YunBQ\backend && java -jar target\backend-0.0.1-SNAPSHOT.jar"

:: 等待 3 秒，让后端先启动一点（可根据情况修改）
timeout /t 3 /nobreak >nul

:: 第二步：打开前端命令行窗口
echo 启动前端服务中...
start "前端命令行窗口" cmd /k "e: && cd Trea_WorkSpace\YunBQ\frontend && npm run dev"


:: 等待 5 秒，确保前端 dev server 启动
timeout /t 10 /nobreak >nul

:: 第三步：自动打开浏览器访问指定地址
echo 正在打开浏览器访问 http://localhost:5173 ...
start "" http://localhost:5173




echo 所有服务已启动！
pause

:: start "窗口标题" cmd /k "命令串"
:: 	start 用来打开一个新的命令行窗口。
:: 	"窗口标题" 是显示在标题栏的名称（方便区分前后端）。
:: 	cmd /k 表示执行完命令后 保持窗口不关闭。
:: 	里面的命令串使用 && 连接多条命令。
:: pause	
:: 	保留主窗口（可选，不影响前后端）。
:: timeout /t 3 /nobreak >nul
:: 	等待 3 秒，不显示倒计时。


:: start "" http://localhost:5173
::	自动用系统默认浏览器打开指定网址。
