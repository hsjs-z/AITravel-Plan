-- Content gallery display fixtures.
-- These rows are isolated by city='图片' and intentionally have no specs or
-- inventory units, so they cannot enter the booking path.
-- Safe to rerun: each row is inserted only when its id is absent.

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-01', 'ATTRACTION', '黄山云海', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-01');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-02', 'ATTRACTION', '张家界武陵源', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-02');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-03', 'ATTRACTION', '桂林漓江', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-03');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-04', 'ATTRACTION', '稻城亚丁', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-04');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-05', 'ATTRACTION', '茶卡盐湖', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-05');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-06', 'ATTRACTION', '喀纳斯湖', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-06');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-07', 'ATTRACTION', '四姑娘山', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-07');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-08', 'ATTRACTION', '霞浦滩涂', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-08');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-09', 'ATTRACTION', '张掖丹霞', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-09');

INSERT INTO resource (id, type, name, destination, status, city, image_url)
SELECT 'r-content-gallery-10', 'ATTRACTION', '纳木错', '图片', 'ON_SHELF', '图片',
       'https://javaself.oss-cn-beijing.aliyuncs.com/f77c597f-0b20-4139-b0d0-8d7928a6fb3f.png'
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE id = 'r-content-gallery-10');
