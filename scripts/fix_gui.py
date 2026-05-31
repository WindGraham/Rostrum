import re

with open('gui_omnimaster_phone.html', 'r') as f:
    html = f.read()

# Fix 1: Folder grid adaptive columns
html = html.replace(
    '.folder-grid {\n      grid-template-columns: 1fr 1fr;\n    }',
    '.folder-grid {\n      display: grid;\n      grid-template-columns: repeat(auto-fill, minmax(60px, 1fr));\n      column-gap: 6px;\n      row-gap: 10px;\n    }'
)

# Fix 2: Add compact mode CSS for AI panel
compact_css = '''    .chat-panel.compact .welcome-section,
    .chat-panel.compact .quick-actions {
      display: none;
    }
    .chat-panel.compact .header {
      height: 36px;
      padding: 0 10px;
    }
    .chat-panel.compact .header-title {
      font-size: 14px;
    }
    .chat-panel.compact .toolbar {
      height: 32px;
      padding: 0 6px;
    }
    .chat-panel.compact .toolbar-btn {
      width: 26px;
      height: 26px;
      font-size: 14px;
    }
    .chat-panel.compact .input-area {
      height: 44px;
      padding: 0 6px;
    }
    .chat-panel.compact .input-field {
      height: 32px;
    }
    .chat-panel.compact .send-btn {
      width: 32px;
      height: 32px;
    }
    .chat-panel.compact .message-bubble {
      font-size: 12px;
      padding: 6px 10px;
    }
'''

html = html.replace(
    '    /* ======== AI Panel Compact Mode ======== */',
    '    /* ======== AI Panel Compact Mode ======== */\n' + compact_css
)

# Fix 3: Update updateLayout function to handle compact mode
old_update = '''      function updateLayout() {
        updatePanesAndSplitters();
        updateBallPosition();
        
        // ===== Adaptive Content =====
        const paneBHeight = parseFloat(paneB.style.height);
        const chatPanel = paneB.querySelector('.chat-panel');
        if (chatPanel) {
          if (paneBHeight < 260) {
            chatPanel.classList.add('compact');
          } else {
            chatPanel.classList.remove('compact');
          }
        }
        
        // Update folder grid columns based on pane width
        const paneTLWidth = parseFloat(paneTL.style.width);
        const folderGrids = document.querySelectorAll('.folder-grid');
        folderGrids.forEach(grid => {
          if (paneTLWidth < 140) {
            grid.style.gridTemplateColumns = 'repeat(auto-fill, minmax(52px, 1fr))';
          } else if (paneTLWidth < 200) {
            grid.style.gridTemplateColumns = 'repeat(2, 1fr)';
          } else {
            grid.style.gridTemplateColumns = 'repeat(auto-fill, minmax(64px, 1fr))';
          }
        });
      }'''

new_update = '''      function updateLayout() {
        updatePanesAndSplitters();
        updateBallPosition();
        
        // ===== Adaptive AI Panel =====
        const paneBHeight = parseFloat(paneB.style.height);
        const chatPanel = paneB.querySelector('.chat-panel');
        if (chatPanel) {
          // Calculate minimum required height for non-scrollable elements
          const headerH = chatPanel.querySelector('.header')?.offsetHeight || 48;
          const toolbarH = chatPanel.querySelector('.toolbar')?.offsetHeight || 44;
          const welcomeH = chatPanel.querySelector('.welcome-section')?.offsetHeight || 0;
          const quickH = chatPanel.querySelector('.quick-actions')?.offsetHeight || 0;
          const inputH = chatPanel.querySelector('.input-area')?.offsetHeight || 56;
          const minFullHeight = headerH + toolbarH + welcomeH + quickH + inputH + 40; // +40 for some message space
          
          if (paneBHeight < minFullHeight) {
            chatPanel.classList.add('compact');
          } else {
            chatPanel.classList.remove('compact');
          }
          
          // Ensure message list takes available space
          const msgList = chatPanel.querySelector('.message-list');
          if (msgList) {
            msgList.style.maxHeight = (paneBHeight - headerH - toolbarH - inputH - 10) + 'px';
          }
        }
        
        // ===== Adaptive Folder Grid =====
        const paneTLWidth = parseFloat(paneTL.style.width);
        const folderGrids = document.querySelectorAll('.folder-grid');
        folderGrids.forEach(grid => {
          if (paneTLWidth < 120) {
            grid.style.gridTemplateColumns = 'repeat(auto-fill, minmax(48px, 1fr))';
          } else if (paneTLWidth < 180) {
            grid.style.gridTemplateColumns = 'repeat(2, 1fr)';
          } else {
            grid.style.gridTemplateColumns = 'repeat(auto-fill, minmax(60px, 1fr))';
          }
        });
      }'''

if old_update in html:
    html = html.replace(old_update, new_update)
else:
    print("WARNING: Could not find old updateLayout block, trying alternative match")
    # Try simpler match
    simple_old = '''      function updateLayout() {
        updatePanesAndSplitters();
        updateBallPosition();
      }'''
    if simple_old in html:
        html = html.replace(simple_old, new_update)
        print("Replaced simple updateLayout")
    else:
        print("ERROR: Could not find updateLayout function")

# Fix 4: Ensure message-list has proper overflow
html = html.replace(
    '.chat-panel .message-list {\n      flex: 1;\n      overflow-y: auto;\n    }',
    '.chat-panel .message-list {\n      flex: 1;\n      overflow-y: auto;\n      min-height: 0;\n    }'
)

# Fix 5: Add more realistic folder data
old_folders = '''    const folders = [
      { name: "我的文件", gradient: 1 },
      { name: "备份类", gradient: 2 },
      { name: "代码类", gradient: 1 },
      { name: "待处理文件", gradient: 2 },
      { name: "功能演示", gradient: 1 },
      { name: "归档文件", gradient: 2 },
      { name: "媒体类", gradient: 1 },
      { name: "图片类", gradient: 2 },
      { name: "网页类", gradient: 1 },
      { name: "文档类", gradient: 2 },
      { name: "小说_记忆贩卖者", gradient: 1 },
      { name: "演示备份文件夹类", gradient: 2 },
      { name: "应用类", gradient: 1 },
      { name: "AI_Assistant_Demo", gradient: 2 },
    ];'''

new_folders = '''    const folders = [
      { name: "我的文件", gradient: 1 },
      { name: "备份类", gradient: 2 },
      { name: "代码类", gradient: 1 },
      { name: "待处理文件", gradient: 2 },
      { name: "功能演示", gradient: 1 },
      { name: "归档文件", gradient: 2 },
      { name: "媒体类", gradient: 1 },
      { name: "图片类", gradient: 2 },
      { name: "网页类", gradient: 1 },
      { name: "文档类", gradient: 2 },
      { name: "小说_记忆", gradient: 1 },
      { name: "演示备份", gradient: 2 },
      { name: "应用类", gradient: 1 },
      { name: "AI_Demo", gradient: 2 },
      { name: "下载目录", gradient: 1 },
      { name: "音乐库", gradient: 2 },
      { name: "视频剪辑", gradient: 1 },
      { name: "项目源码", gradient: 2 },
      { name: "学习笔记", gradient: 1 },
      { name: "临时文件", gradient: 2 },
    ];'''

html = html.replace(old_folders, new_folders)

with open('gui_omnimaster_phone.html', 'w') as f:
    f.write(html)

print("Fixes applied successfully!")
