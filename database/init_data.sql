USE campus_recycle;
INSERT INTO users(username,password_hash,real_name,phone,role) VALUES
('admin','$2a$10$admin-demo-hash','系统管理员','13800000000','ADMIN'),
('student01','$2a$10$user-demo-hash','张同学','13800000001','USER');
INSERT INTO categories(name,point_factor) VALUES ('教材书籍',1.20),('电子产品',1.50),('生活用品',1.00),('衣物',1.10),('文体用品',1.30),('其他',0.80);
INSERT INTO recycling_stations(name,location,phone,accepted_categories) VALUES ('南苑回收点','南苑宿舍区东门','027-12345678','教材书籍、生活用品、衣物'),('图书馆回收点','图书馆一楼','027-12345679','教材书籍、电子产品');
INSERT INTO items(owner_id,category_id,name,description,condition_level,original_price,current_price,estimated_recycle_price,process_type,status) VALUES (2,1,'数据库系统原理教材','九成新，含课堂笔记','GOOD',68,25,8,'SELL','PENDING'),(2,3,'台灯','正常使用，亮度可调','GOOD',80,0,10,'GIVE','PENDING');
