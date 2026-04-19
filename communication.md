
[4:46 pm, 19/4/2026] Arghya: java -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xlog:gc Test
[5:25 pm, 19/4/2026] Arghya: find . -type f -name "*.class" -exec ls -l {} + | awk '{total += $5} END {print total " bytes"}'
[5:47 pm, 19/4/2026] Arghya: jol on the correct classes
[5:50 pm, 19/4/2026] Arghya: time java -Xint Test
[5:51 pm, 19/4/2026] Arghya: and without -Xint

JOL: java -jar jol-cli-latest.jar internals -cp . 

# Test 10 11 12 13 results

Test13: (no change testcases all fields used)
folder op:
[1.439s][info   ][gc     ] Heap: 3925M reserved, 501M (12.77%) committed, 460M (11.74%) used
2708 bytes
Car: Instance size: 40 bytes
SportsCar: Instance size: 48 bytes
Vehicle: Instance size: 24 bytes
sootOutput:
[1.439s][info   ][gc     ] Heap: 3925M reserved, 501M (12.77%) committed, 460M (11.74%) used
10615 bytes
CarInterface: Instance size: 16 bytes
Car_SAB_Cardoors_Vehiclespeed_Carengine_Vehicleid_Carseats_Vehicleyear: Instance size: 40 bytes
SportsCarInterface: Instance size: 16 bytes
SportsCar_SAB_Cardoors_SportsCarturbo_Vehiclespeed_SportsCarnitro_Carengine_SportsCarrating_Vehicleid_Carseats_Vehicleyear: Instance size: 48 bytes
TestInterface: Instance size: 16 bytes
VehicleInterface: Instance size: 16 bytes
Vehicle_SAB_Vehiclespeed_Vehicleid_Vehicleyear: Instance size: 24 bytes

Test12:
folder op:
[0.154s][info   ][gc     ] Heap: 3925M reserved, 373M (9.51%) committed, 346M (8.83%) used
1802 bytes
Test: Instance size: 16 bytes
DataContainer: Instance size: 24 bytes
DataItem: Instance size: 32 bytes
sootOutput:
[0.139s][info   ][gc     ] Heap: 3925M reserved, 373M (9.51%) committed, 270M (6.90%) used
5938 bytes
DataContainer_SAB_DataContaineritems: Instance size: 16 bytes
DataItemInterface: Instance size: 16 bytes
DataContainerInterface: Instance size: 16 bytes
DataItem_SAB_DataItemkey_DataItemvalue: Instance size: 24 bytes
TestInterface: Instance size: 16 bytes

Test11:
folder op:
[0.072s][info   ][gc     ] Heap: 3925M reserved, 245M (6.25%) committed, 7455K (0.19%) used
1761 bytes
Rectangle: Instance size: 32 bytes
Shape: Instance size: 24 bytes
Square: Instance size: 40 bytes
Test: Instance size: 16 bytes
sootOutput:
[0.086s][info   ][gc     ] Heap: 3925M reserved, 245M (6.25%) committed, 6156K (0.15%) used
7502 bytes
ShapeInterface: Instance size: 16 bytes
Square_SAB_Shapeid_Rectanglewidth_Rectangleheight_Squareside: Instance size: 32 bytes
RectangleInterface: Instance size: 16 bytes
Shape_SAB_Shapeid: Instance size: 16 bytes
Rectangle_SAB_Shapeid_Rectanglewidth_Rectangleheight: Instance size: 24 bytes
TestInterface: Instance size: 16 bytes
SquareInterface: Instance size: 16 bytes

Test 10:
folder op:
[0.138s][info   ][gc     ] Heap: 3925M reserved, 245M (6.25%) committed, 19389K (0.48%) used
2014 bytes
AggMetric: Instance size: 56 bytes
Metric: Instance size: 24 bytes
Test: Instance size: 16 bytes
TimedMetric: Instance size: 40 bytes
sootOutput:
[0.149s][info   ][gc     ] Heap: 3925M reserved, 245M (6.25%) committed, 10930K (0.27%) used
10405 bytes
AggMetrircInterface: Instance size: 16 bytes
AggMetric_SAB_AggMetriccount_MetricmetricId_TimedMetricvalueMs: Instance size: 24 bytes
TimedMetricInterface: Instance size: 16 bytes
MetricInterface: Instance size: 16 bytes
TimedMetric_SAB_MetricmetricId_TimedMetricvalueMs: Instance size: 24 bytes
Metric_SAB_MetricmetricId: Instance size: 16 bytes


# read this
check ./transformation-testing for initial transformation code.

# Test case description
Test2 : checking multiple fields
Test3: checking changes to parent fields
Test4: statics test

# READ
getter part is complete, and interface method signatures also fixed

i've added basic setters to baseClassses,
question:
- do what kind of setters do we need for Arrays? Added setter to set according to index for now, also added basic setter to set array reference
work left:
- setters in deaders need to be modified to match class types
- setters and getters in baseclasses need to match <classname>Interface types in return type and getter/setter bodies