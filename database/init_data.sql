USE campus_recycle;
INSERT INTO users(username,password_hash,real_name,phone,role) VALUES
('admin','pbkdf2$600000$YWRtaW4tZGVtby1oYXNoLQ==$ZGjA0y2N30E2KNIQ+8jwoe656JRKCJ3vWKkY7yghDKE=','系统管理员','13800000000','ADMIN'),
('student01','pbkdf2$600000$dXNlci1kZW1vLWhhc2gtZw==$5xfWsH7hk/gDTW8reVKc9x9AiamPkl3hfTojq8iZAMQ=','张同学','13800000001','USER');
INSERT INTO categories(name,point_factor) VALUES ('教材书籍',1.20),('电子产品',1.50),('生活用品',1.00),('衣物',1.10),('文体用品',1.30),('其他',0.80);
INSERT INTO recycling_stations(name,location) VALUES ('南苑回收点','南苑宿舍区东门'),('图书馆回收点','图书馆一楼');
INSERT INTO items(owner_id,category_id,name,description,current_price,process_type,meeting_date,meeting_time,meeting_location,status) VALUES (2,1,'数据库系统原理教材','九成新，含课堂笔记',25,'SELL',DATE_ADD(CURRENT_DATE(),INTERVAL 7 DAY),'14:00','南苑一站式','APPROVED'),(2,3,'台灯','正常使用，亮度可调',0,'GIVE',DATE_ADD(CURRENT_DATE(),INTERVAL 7 DAY),'14:00','北苑食堂','APPROVED');
