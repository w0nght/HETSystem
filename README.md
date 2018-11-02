# Home Energy Trading Stsyem (HETSystem)
This project is the assignment of Swinburne University unit COS30018.

## Project Introduction
This project is to implement and demonstrate a simple home energy trading system. </br>
The home will be able to procure energy from retailer according to the estimated demand of the appliances. </br>
The agents will be represent the appliances, the home and the retailers.</br>
The objective of the home is to accurately estimate the required energy and procure it at the lowest possible price.</br>

## Project Participants
| Name                  | Student no.    |                     Github                   |
| --------------------- | -------------- |----------------------------------------------|
| Hei Tung Wong         |    101664795   | [w0nght](https://github.com/w0nght)          |
| Kieu Que Thanh Nguyen |    101354326   | [wedproject](https://github.com/wedproject)  |

### Add JADE libraries to project
All jar files are located in the lib folder.  </br>
If the project settings not up-to-date, please go to the project properties. </br>
in the project Workspace, right click on the project, go under Build Path > Configure Build Path... </br>
In the project properties window, go to Java Biild Path > Libraries > Add JARs... </br>
select all jar file from the lib folder > OK > Apply and Close  </br>

Run Configuration > Java Application > Configuration

Main Class: `jade.Boot`

Program Arguments: `-gui
-agents
home:agents.HomeAgent;energyWatch:agents.linearRA;assuieEnergy:agents.highRA;dodo:agents.randomRA;desktop:agents.ApplianceAgent;bedroom:agents.ApplianceAgent;airCondition:agents.ApplianceAgent;toliet:agents.ApplianceAgent
`
