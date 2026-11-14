        document.addEventListener('DOMContentLoaded', function() {
            var calendarEl = document.getElementById('calendar');
            var selectedDateEl = document.getElementById('selectedDate');
            var currentSelectedDate = null;
            
            var calendar = new FullCalendar.Calendar(calendarEl, {
                initialView: 'dayGridMonth',
                locale: 'zh-cn',
                headerToolbar: {
                    left: 'prev,next today',
                    center: 'title',
                    right: 'dayGridMonth,timeGridWeek,timeGridDay'
                },
                // 点击日期时触发
                dateClick: function(info) {
                    // 移除之前的高亮
                    if (currentSelectedDate) {
                        var prevSelected = document.querySelector('.fc-day[data-date="' + currentSelectedDate + '"]');
                        if (prevSelected) prevSelected.classList.remove('highlight');
                    }
                    
                    // 添加新选择的高亮
                    info.dayEl.classList.add('highlight');
                    
                    // 更新选择的日期
                    currentSelectedDate = info.dateStr;
                    selectedDateEl.textContent = formatChineseDate(info.date);
                    
                    // 你也可以在这里执行其他操作，如提交表单等
                    // console.log('选择的日期:', info.dateStr);
                },
                // 初始化后添加今天的高亮
                datesSet: function() {
                    if (currentSelectedDate) {
                        var selectedDay = document.querySelector('.fc-day[data-date="' + currentSelectedDate + '"]');
                        if (selectedDay) selectedDay.classList.add('highlight');
                    }
                }
            });
            
            calendar.render();
            
            // 格式化日期为中文显示
            function formatChineseDate(date) {
                var year = date.getFullYear();
                var month = date.getMonth() + 1;
                var day = date.getDate();
                var weekdays = ['日', '一', '二', '三', '四', '五', '六'];
                var weekday = weekdays[date.getDay()];
                
                return year + '年' + month + '月' + day + '日 星期' + weekday;
            }
        });