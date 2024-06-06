/*
--drop tables if required source tables if needed
DROP TABLE temp.tbl_serv_pwr_actual;
DROP TABLE temp.tbl_serv_inv;
DROP TABLE temp.tbl_ecf;
DROP TABLE temp.tbl_util;
DROP TABLE temp.tbl_vm_inv;
DROP TABLE temp.tbl_actual_ec;

--if process created to import redfish api output into db, please use and name temp.tbl_serv_pwr_actual
--otherwise below block creates a dummy table replicating data structure
CREATE TABLE temp.tbl_serv_pwr_actual (ts timestamp,servername string,minconsumedwatts float64,averageconsumedwatts float64,maxconsumedwatts float64,intervalinmin float64);
INSERT INTO temp.tbl_serv_pwr_actual VALUES
('2024-04-17 05:37:47','GB00001',650,700,750,20),
('2024-04-17 05:37:48','GB00002',500,550,575,20),
('2024-04-17 05:37:49','GB00005',550,600,650,20),
('2024-04-17 05:57:50','HK00001',600,700,750,20),
('2024-04-17 05:57:49','GB00001',650,680,750,20),
('2024-04-17 05:57:50','GB00002',500,510,520,20),
('2024-04-17 05:57:50','GB00005',550,610,650,20),
('2024-04-17 05:57:50','HK00001',600,700,750,20);

--create additional tables for use in calculation
--example server inventory
CREATE TABLE temp.tbl_serv_inv (period timestamp,clustername string,servername string,type string,datacenter string,locationname string,watts float64);
INSERT INTO temp.tbl_serv_inv VALUES
('2024-04-17','Cluster1','GB00001','compute','dc1','United Kingdom',800),
('2024-04-17','Cluster1','GB00002','compute','dc1','United Kingdom',600),
('2024-04-17','Cluster1','GB00003','compute','dc1','United Kingdom',700),
('2024-04-17',null,'GB00004','compute','dc1','United Kingdom',610),
('2024-04-17',null,'GB00005','storage','dc1','United Kingdom',700),
('2024-04-17',null,'GB00006','storage','dc1','United Kingdom',750),
('2024-04-17',null,'GB00007','network','dc1','United Kingdom',800),
('2024-04-17',null,'HK00001','network','dc2','Hong Kong',800),
('2024-04-17',null,'HK00002','compute','dc2','Hong Kong',750);

--example emission factors
CREATE TABLE temp.tbl_ecf (country string,loc_m float64,mar_m float64);
INSERT INTO temp.tbl_ecf VALUES
('Hong Kong',0.47,0.461),
('United Kingdom',0.21059,0.008);

--example monthly utilisation data
CREATE TABLE temp.tbl_util (period timestamp,servername string,cpu_util float64);
INSERT INTO temp.tbl_util VALUES
('2024-04-01','GB00001',8),
('2024-03-01','GB00002',8),
('2024-04-01','GB00003',7),
('2024-04-01','GB00004',7),
('2024-04-01','GB00005',9),
('2024-04-01','GB00006',15),
('2024-04-01','GB00007',8),
('2024-04-01','HK00001',8),
('2024-04-01','HK00002',30),
('2024-04-01','GBVM001',13),
('2024-04-01','GBVM002',6),
('2024-04-01','GBVM003',46),
('2024-04-01','GBVM004',33),
('2024-04-01','GBVM005',12),
('2024-04-01','GBVM006',12),
('2024-04-01','GBVM007',15);

--example vm inventory data
CREATE TABLE temp.tbl_vm_inv (period timestamp,servername string,vm_name string,cpu_cores float64);
INSERT INTO temp.tbl_vm_inv VALUES
('2024-04-17','GB00001','GBVM001',2),
('2024-04-17','GB00001','GBVM002',2),
('2024-04-17','GB00001','GBVM003',2),
('2024-04-17','GB00002','GBVM004',2),
('2024-04-17','GB00002','GBVM005',2),
('2024-04-17','GB00003','GBVM006',2),
('2024-04-17','GB00003','GBVM007',2);

--example actual energy usage at a datacenter level
CREATE TABLE `hsbc-1655143-carbontrack-dev.temp.tbl_actual_ec` (period timestamp,datacenter string,building_energy float64,it_energy float64);
INSERT INTO `hsbc-1655143-carbontrack-dev.temp.tbl_actual_ec` VALUES
('2024-04-17','dc1',0.07,0.15),
('2024-04-17','dc2',0.02,0.04);

CREATE OR REPLACE FUNCTION temp.pf(u FLOAT64) RETURNS FLOAT64
OPTIONS (description="Input is CPU utilization and output is corresponding power factor.") AS (
CASE
  WHEN u <= 5 THEN 0.3
  WHEN u<= 15 THEN 0.42
  WHEN u<= 25 THEN 0.46
  WHEN u<= 35 THEN 0.51
  WHEN u<= 45 THEN 0.56
  WHEN u<= 55 THEN 0.61
  WHEN u<= 65 THEN 0.68
  WHEN u<= 75 THEN 0.74
  WHEN u<= 85 THEN 0.82
  WHEN u<= 95 THEN 0.91
  WHEN u > 95 AND u<= 100 THEN 1
  ELSE 0
END
);

*/

DECLARE
targetdate TIMESTAMP DEFAULT '2024-04-17';

--calculate the average power for each server in redfish output
DROP TABLE temp.tbl_serv_pwr;
CREATE TABLE temp.tbl_serv_pwr AS (SELECT s.period
                                        , 'actual'                  AS origin
                                        , datacenter
                                        , type
                                        , clustername
                                        , s.servername
                                        , locationname
                                        , acw
                                        , mm
                                        , acw * 1440 / 60 / 1000000 AS bup
                                   FROM temp.tbl_serv_inv AS s
                                            LEFT JOIN (
                                       --this table calculates the average power based on redfish output
                                       SELECT TIMESTAMP_TRUNC(ts, Day)  AS period,
                                              servername,
                                              avg(averageconsumedwatts) as acw,
                                              sum(intervalinmin)        as mm
                                       FROM temp.tbl_serv_pwr_actual
                                       WHERE TIMESTAMP_TRUNC(ts, Day) = targetdate
                                       GROUP BY TIMESTAMP_TRUNC(ts, Day), servername) AS p
                                                      on s.servername = p.servername and p.period = s.period
                                   WHERE acw is not null);

--SELECT * FROM temp.tbl_serv_pwr

--do the same for servers in the inventory that dont have an actual power measurement
INSERT INTO temp.tbl_serv_pwr
SELECT s.period
     , 'rated'
     , datacenter
     , type
     , clustername
     , s.servername
     , locationname
     , watts
     , 0
     , temp.pf(cpu_util) * watts * 1440 / 60 / 1000000 AS bup
FROM (SELECT *
      FROM temp.tbl_serv_inv
      WHERE period = targetdate
        and servername not in (SELECT distinct servername FROM temp.tbl_serv_pwr WHERE period = targetdate)) AS s
         LEFT JOIN (SELECT period, servername, cpu_util
                    FROM temp.tbl_util) AS u
                   on s.servername = u.servername and extract(year from s.period) = extract(year from u.period) and
                      extract(month from s.period) = extract(month from u.period);

--SELECT * FROM temp.tbl_serv_pwr

--split cluster_bup into vms
DROP TABLE temp.tbl_serv_pwr_vm;
CREATE TABLE temp.tbl_serv_pwr_vm AS (WITH tbl_vm AS (SELECT clustername,
                                                             v.servername,
                                                             vm_name,
                                                             cpu_cores,
                                                             temp.pf(cpu_util) as pf,
                                                             cpu_util
                                                      FROM temp.tbl_vm_inv v
                                                               LEFT JOIN temp.tbl_util u ON v.vm_name = u.servername and
                                                                                            extract(year from v.period) =
                                                                                            extract(year from u.period) and
                                                                                            extract(month from v.period) =
                                                                                            extract(month from u.period)
                                                               LEFT JOIN temp.tbl_serv_inv i ON i.servername = v.servername)
                                      SELECT p.period
                                           , datacenter
                                           , type
                                           , p.clustername
                                           , p.servername
                                           , locationname
                                           , vm_name
                                           , CASE
                                                 WHEN p.clustername is not null
                                                     THEN cluster_bup * (cpu_cores * tbl_vm.pf) / tot_pf
                                                 ELSE bup END AS bup
                                      --uncomment below line to see individual elements used for sharing our pwr across cluster
                                      --,cluster_bup,cpu_cores,cpu_util,tbl_vm.pf,tot_pf
                                      FROM temp.tbl_serv_pwr AS p
                                               LEFT JOIN tbl_vm ON p.servername = tbl_vm.servername
                                               LEFT JOIN (SELECT clustername, sum(bup) AS cluster_bup
                                                          FROM temp.tbl_serv_pwr
                                                          WHERE clustername is not null
                                                          GROUP BY clustername) AS c ON c.clustername = p.clustername
                                               LEFT JOIN (SELECT clustername, sum(pf * cpu_cores) as tot_pf
                                                          FROM tbl_vm
                                                          GROUP BY clustername) v_pf
                                                         on v_pf.clustername = p.clustername);
--estimate energy split by type based on maximum rated power
DROP TABLE temp.tbl_dc_pwr_split;
CREATE TABLE temp.tbl_dc_pwr_split AS (SELECT tbl_serv_inv.datacenter,
                                              type,
                                              sum(watts)             AS watts,
                                              sum(watts) / tot_watts AS pwr_perc
                                       FROM temp.tbl_serv_inv
                                                LEFT JOIN (SELECT datacenter, sum(watts) as tot_watts
                                                           FROM temp.tbl_serv_inv
                                                           WHERE period = targetdate
                                                           GROUP BY datacenter) as tbl_tot
                                                          on tbl_tot.datacenter = tbl_serv_inv.datacenter
                                       WHERE period = targetdate
                                       GROUP BY tbl_serv_inv.datacenter, type, tbl_tot.tot_watts);

--create breakdown of top down energy to be apportioned (etba)
DROP TABLE temp.tbl_dc;
CREATE TABLE temp.tbl_dc AS (SELECT e.period
                                  , e.datacenter
                                  , locationname
                                  , b.type
                                  , it_energy
                                  , building_energy * perc   AS b_etba
                                  , bup
                                  , perc
                                  , (it_energy * perc) - bup AS it_etba
                             FROM temp.tbl_actual_ec e
                                      LEFT JOIN (SELECT datacenter
                                                      , type
                                                      , locationname
                                                      , sum(bup) as bup
                                                 FROM temp.tbl_serv_pwr
                                                 GROUP BY datacenter, type, locationname) AS b
                                                on b.datacenter = e.datacenter
                                      LEFT JOIN (SELECT p.period
                                                      , p.datacenter
                                                      , type
                                                      , sum(watts) / dc_watts AS perc
                                                 FROM temp.tbl_serv_inv p
                                                          LEFT JOIN (SELECT period
                                                                          , datacenter
                                                                          , sum(watts) as dc_watts
                                                                     FROM temp.tbl_serv_inv
                                                                     GROUP BY period, datacenter) AS w
                                                                    ON w.datacenter = p.datacenter
                                                 GROUP BY p.period, p.datacenter, type, dc_watts) AS d
                                                ON e.datacenter = d.datacenter and e.period = d.period and b.type = d.type
                             WHERE e.period = targetdate);

--create final output apportioning overheads and applying location and market based factors
DROP TABLE temp.tbl_output;
CREATE TABLE temp.tbl_output AS (SELECT v.*
                                      , v.bup / d.bup                                                            as perc
                                      , v.bup / d.bup * it_etba                                                  as it_oh
                                      , v.bup / d.bup * b_etba                                                   as b_oh
                                      , v.bup + (v.bup / d.bup * it_etba) + (v.bup / d.bup * b_etba)             as tot_energy
                                      , e.loc_m * (v.bup + (v.bup / d.bup * it_etba) + (v.bup / d.bup * b_etba)) AS loc
                                      , e.mar_m * (v.bup + (v.bup / d.bup * it_etba) + (v.bup / d.bup * b_etba)) AS mar
                                 FROM temp.tbl_serv_pwr_vm AS v
                                          LEFT JOIN temp.tbl_dc AS d
                                                    ON v.datacenter = d.datacenter and v.period = d.period and v.type = d.type
                                          LEFT JOIN temp.tbl_ecf AS e ON e.country = d.locationname);

--SELECT * FROM temp.tbl_output