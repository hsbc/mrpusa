# Measuring Real Power Usage of Software Applications (mrpusa)

Measuring Real Power Usage of Software Applications: open source version 

# Measuring Real Power Usage of Software Methodology

#### MRPUSA is an application that serves as the basis for a software carbon emission measuring initiative. This initiative, a system and method for measuring the real power draw and carbon emissions of software applications, allows businesses and consumers to accurately assess emissions at an individual software level providing a greater level of granularity in reporting.

The core proposition of this initiative is being able to connect a user device to a visualisation platform or service
that is informed by a series of modules that account for the intake of energy consumption data, mapping applications to
devices, attributing power to these applications from their mapped devices and converting the calculated energy consumed
to emission figures.
<br>

### Measuring Real Power Usage of Software Application Overview:

The MRPUSA application serves to configure and access data across our different API endpoints and databases to
aggregate data on our inventory, that is then utilised according to our methodology to arrive at an emission figure for
a given device, cluster or service. Written primarily in Java, using Spring framework and Maven build management, this
application can be broken down at a high level to three components: configuration files, data collection jobs & write
streams for declaring collected data's destination. Currently, write streams and some configuration files are written
and are dependent on Google's BigQuery and its Java libraries. However for this open sourced version, this can be re-examined and should
serve in its current state as a reference point for how this code for data collection could be pushed into one of the
larger Cloud provider's services for further analysis/manipulation & visualisation.

<br>

#### Outlined below is a generalised overview of the methodology, of which is dependent on the data captured by this application:

## Bottom-up: Mapping

| Logical Step                                    | Outline                                                                                                                                                                                                                                                                  |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Hardware <-> Physical Hosts <-> Virtual Hosts` | <ul><li>Get the full list of hardware from device inventory</li><li>Map the hardware to their physical hosts</li><li>Map these physical hosts to virtual hosts via hypervisor</li><li>Map Services and application instances to respective physical / virtual hosts</li> |
| `Utilisation Data`                              | <ul><li>Bring in the utilisation data from the virtual / physical host keys</li><li>Bring in the average CPU</li><li>Where no host utilisation is found, an average estimate is applied</li>                                                                             |
| `Filters`                                       | Remove rows where:<ul><li>Server lifecycle is Demised</li><li>Virtual infrastructure services (VMware) where other services mapped to device</li>                                                                                                                        |

## Bottom-up: Energy Calculation

| Logical Step               | Outline                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Hardware Power Draw`      | Actual Power Draw: <ul><li>Bring in Actual Power draw for mapped physical servers via the power draw data API</li><li>Actual power draw (AverageConsumedWatts) via power draw data API is taken every 20 minutes</li><li>Intervals aggregated up for a daily totals</li> <br>Rated Power Draw - used where no actual power reading was found: </br><br> <li>Maximum Rated Power is adjusted by the power factor at the mapped physical server utilisation range</li> |
| `Maximum Rated Power`      | <ul><li>Device maximum power (in Watts) is joined from Data Center Hardware inventory using the hardware key</li>                                                                                                                                                                                                                                                                                                                                                    |
| `Total Energy Consumption` | <ul><li>Multiply the power draw, Watts x duration, hours /  10^6  to get energy consumption (MWh) per device</li><li>Sum the energy (MWh) to get the yearly energy consumption across the estate </li><li>Apportion the data centre Actual – Estimated delta proportionally across apps </li>                                                                                                                                                                               |

## Top-Down: Overheads

| Logical Step              | Outline                                                                                                                                                                                                                                                                                                                                                     |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Overheads apportionment` | <ul><li>IT Energy estimated using the bottom up approach is lower than the IT Energy known to be consumed by the data centre</li><li>To account for this, IT Energy delta (Compute, Storage, Network and Other) and Building Energy is apportioned back to the data centres</li><li>Apportionment is performed on the application instance count basis</li> |

## Emissions

| Logical Step         | Outline                                                                                                                                                        |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Energy → Emissions` | <ul><li>Carbon equivalent emissions are obtained by considering energy consumed and the emissions factor (location and market based) for a given location</li> |

## Bottom-Up IT Energy calculation Overview

Bottom-Up approach refers to the IT energy consumption which is derived by estimating the server energy consumption.
This is achieved by two main methods:

### Actual Power method:

Refers to energy consumption obtained directly from physical servers

### Rated Power method (for physical servers with no actual energy consumption data available)

Refers to energy consumption estimated, where Maximum Rated device power (Data Centre Device Data) is scaled down by the
Power Factor. Power Factor is derived based on the utilisation % of a given server.

### Device / physical server power draw is then apportioned to mapped downstream elements:

- Directly to mapped applications
- To applications via VMs, where servers are clustered

### Actual Energy Consumption Calculation

Getting a device's 'Actual' energy consumption is made possible only by power draw API, which may not be configured for
all devices. Below is the logical steps taken in order to get this data for devices where this configuration is present.

| Step | Method                                                                                                                                |
|------|---------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Retrieve a Fully Qualified Domain Name of physical servers from device inventory                                                      |
| 2    | Using the FQDN, retrieve a given physical server's power draw from power draw API                                                     |
| 3    | Multiply the average consumed power draw (Watts) x Interval (mins) /  60 * 1.0E-6 to get energy consumption (MWh) per physical server |

### Rated Energy Consumption Calculation

Where device power data is not configured for a device within an inventory, we have to draw from other data sources in
order to gauge a device's power draw estimate.

| Step | Method                                                                                                                                                                                                                                                                                         |
|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Bring in hardware inventory list. Maximum power (in Watts) is joined from inventory using the hardware key                                                                                                                                                                                     |
| 2    | Maximum Power is adjusted by the power factor at the utilisation range: <br>One Physical host per device: utilization range assumed to equal host utilization</br>(Where no utilization data can be found for a given device, an average utilization figure based on existing data is applied) |
| 3    | Multiply the average consumed power draw (Watts) x Interval (mins) /  60 * 1.0E-6 to get energy consumption (MWh) per physical server                                                                                                                                                          |
| 4    | Sum the energy (MWh) to get the daily/weekly energy consumption                                                                                                                                                                                                                                |

### Actual Energy Apportionment

The Actual energy is then apportioned out to give a picture of how this energy is consumed per application across
virtual machines and clusters.

Note: downstream energy apportionment logic is the same regardless which method was used to estimate the device power
draw (actual / rated)

| Step | Method                                                                                                                                                                                                                                                                                              |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Physical servers power / Cluster power (Cluster power is summed up by its mapped physical servers power) is apportioned across mapped Virtual Machines based on Power Factor and Virtual Cores of the Virtual Machines, adjusted by the sum of Power Factor x Virtual Cores of the physical servers |
| 2    | Partitioned by the number of mapped services / apps to avoid double counting                                                                                                                                                                                                                        |

## Top-Down

### Overview

Our Top-Down approach contextualises our Bottom-Up calculations within their respective Data estate locations, as it
provides information on other energy usage outside of compute servers. The comparison of data between these two
approaches also gives insight into the accuracy of our calculations and method as we are provided a ''Data Gap'' that
refers to parts of the estate our bottom-up approach has not accounted for.

#### Definition of important key terms required for calculation:

Building Energy Consumption: Total energy consumed by office buildings (in MWh) in each Data Centre
<br></br>
IT resources Energy Consumption: Total energy consumed by IT Resources (in MWh) in each Data Centre
<br></br>
Bottom-Up Energy Estimate: Sum of all actual or estimated energy consumption (in MWh) calculated for the servers located
in the Data Centre.
<br></br>
ttFractions of Overheads: Percentage of calculated energy consumed by IT Compute, IT Storage, IT Network and IT Others
and Buildings, as provided by Data Centre.
<br></br>
Energy Consumption of Applications: Amount bottom-up calulated energy consumption of applications hosted on servers in
each Data Centre, respectively apportioned to their compute categories.
<br></br>
Apportion data Centre overhead per Application: Portion of Energy consumed (in MWh) by each application mapped to Data
Centre.

### Overhead Apportionment

Below details the calculation methodology of Overhead Energy consumption at Data Centre and Application Level and how it
is apportioned.

| Step | Method                                                                                                                                                             |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Get Energy Consumption Delta per Data Centre, calculated by IT Energy (actual) - Bottom-Up energy estimate                                                         |
| 2    | Distribute the Energy Consumption Delta on IT resources (Fractions of Overheads, split as per Data Centre Data Groupings - e.g. Compute, Storage, Network, Others) |

### Emission Factors

An emission factor gives the relationship between the amount of a pollutant produced, and the amount of raw material
processed or burnt.

This methodology is designed for the reporting of Scope 2 emissions under the location-based and market-based methods of
the GHG Protocol.

#### Location-Based

A location-based method reflects the average emissions intensity of grids on which energy consumption occurs (using
mostly grid-average emission factor data). For location based emission reporting; HSBC apply the following hierarchy of
emission factors:

Regional or subnational emission factors
National production emission factors provided by the International Energy Agency (IEA).

#### Market-Based

A market-based method reflects emissions from electricity that companies have purposefully chosen deriving emission
factors from contractual instruments. For market based emissions reporting, HSBC apply the following hierarchy of
emission factors (this may differ in your organsation):

- Factors provided by electricity attribute certificates or equivalent instruments
- Factors provided by contracts for electricity, such as power purchase agreements (PPAs)
- Factors provided by energy suppliers
- Factors provided by the Association of Issuing Bodies (AIB) for the residual mixes in Europe - Version 1.0, 2021-05-31
- Other grid-average emission factors (subnational or national)
- Factors provided by the International Energy Agency (IEA) - 2020 edition of the Emission factors.

#### Market / location based emission factors that is applied to the overall energy calculated:

Carbon Emission in tCO2e = MWh * EF (in tCO2e/Mwh)

## Visualization

The data collected via an application such as this should be easily connected to a visualisation platform, with the
ability to create and seperate views by splitting and filtering data accordingly.

Within our example, we have streamlined this process by working within Google's services, directly connecting our tables
of collated data hosted in their BigQuery product to their Looker visualisation platform with no additional manipulation
required.

However, this should only be understood as an example and other means of visually presenting data collected via this
methodology can be achieved and distributed as per your/your users' needs.

# Measuring Real Power Usage of Software Application Project :

# PART-1:Execute JAVA APPLICATION

# PREREQUISITES:

- Install JDK 11 and set up the JAVA_HOME environment variable correctly
- Install Maven and set up the MAVEN_HOME environment variable and add the MAVEN bin directory to your system path.
- Install git and configure it with your gitHub credentials.
- clone the project repository in your local Machine.
- Download and install an IDE like Eclipse or Intellij IDEA
- after cloning the project from the GitHub navigate to the project directory
- Download all dependencies required for the project, ref.pom.xml file to ensure all dependencies are specified
  correctly and downloaded.

# Project directory Structure:

| Folder     | Description                                                                                                                                                                                                                                                                                                                                                                     |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| config     | the configuration files serve to define access to API endpoints and manages these connections, set schedules for accessing specified data sources.**_ScheduleConfig_**-Sets scheduled tasks for running the various data collector files,**_SwaggerConfig_**-Configures swagger API access within our project,**_WebConfig_**-Handles connections for web based data transfers. |
| Controller | Contains structure of handling request and response starcure with there status code.                                                                                                                                                                                                                                                                                            |
| Service    | **_PhysicalServerCollector.java_**-Contain the code to fecth data from the redfish API for specific hostname and include functionality to filter data and add devices to hostname.**_PowerMetricCollector.java_**-  fetch power metric data for the same hostname                                                                                                               |
| util       | Contain the credentials used for the utilization of API and also the main spring boot class.                                                                                                                                                                                                                                                                                    |
| resources  | **_input_**-power-metric data in json format,**_output_**-contains conversion of json into csv format as output of PowerMetric data,**_Application.yml_**-contains various credetials and URLs required to fetch the data                                                                                                                                                       |
| test       | contains small Junit tests required for json to csv code                                                                                                                                                                                                                                                                                                                        |
| pom.xml    | Our dependencies for this project are declared in the Maven wrapper's Pom.xml file located within the MRPUSA-main folder, along with declaring runtime version for the application.                                                                                                                                                                                     |

# Running the Application:

### RUNNING MRPUSA 
- Ensure JDK,Maven,GitHub,and MRPUSA project set up properly in IDE tool.
- Run spring boot application(MRPUSA app).
- Open postman or swagger and setup all credentials and URLs, Ref. controller class for structure.
- In Postman or Swagger,trigger the endpoint "OPENSOURCE_GENERIC", 
  It will convert the power-metric data response stored in JSON (src/main/resources/input/powermetricData.json) into CSV file as output (src/main/resources/output/physical_server_powermetric.csv).

### RUNNING MRPUSA APP
- Ensure JDK,Maven,GitHub,and MRPUSA project set up properly in IDE tool.
- Replace all the credentials and URLs in the application.yml and in util file.
- Add server_name/hostname of which you want to fetch power metric data into application-data.yaml file
  inside <servar name> tag.
- Run spring boot application(MRPUSA app).
- As per the scheduler,it triggers "PHYSICAL_SERVER" endpoint which start collecting server list and 
  power-metric data of that servers for every 20 min.

# PART-2:Implement SQL OPERATIONS

- Create table which calculates the average power based on redfish output.
- Create table which calculates the average power for servers in the inventory that don’t have an actual power
  measurement.
- Create table which split clustered Bottom-up energy on VMs.
- Create table which estimate energy split by type based on maximum rated power.
- Create table which calculate breakdown of top-down energy to be apportioned.
- Final stage is apportioning overheads and applying location and market-based factors.
