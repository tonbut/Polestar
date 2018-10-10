<?xml version="1.0" encoding="UTF-8" ?>
<!--
   Copyright 2015 1060 Research Ltd

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:nk="http://1060.org">
    <xsl:output method="xml"/>
    
    <xsl:template match="/sensor">
    		<div class="container sensorDetail">
    			<style>
    			.btn-group button { padding-left: 0.5em; padding-right: 0.5em; }
    			</style>
 			<xsl:call-template name="js"/>
 			<div class="row"><div class="col-sm-12">
	    			<table><tr>
	    				<td><img src="{defn/icon}"/></td>
	    				<td><h1><xsl:value-of select="defn/name"/></h1><div class="label label-default"><xsl:value-of select="id"/></div></td>
	    			</tr></table>
	    		</div></div>
 				<div class="row">
	 				<div class="col-sm-12">
	    				<div id="dataChart"/>
	    			</div>
	    		</div>
	    		<div class="row">
	 				<div class="col-sm-12">
	    				<div id="errorChart"/>
	    			</div>
	    		</div>
	    		
	    		<div class="row">
	 			<div class="col-sm-12">
	 				<div class="btn-group" role="group" id="periods">
						<button id="hour" type="button" class="btn btn-default">hour</button>
						<button id="day" type="button" class="btn btn-default">day</button>
						<button id="week" type="button" class="btn btn-default">week</button>
						<button id="month" type="button" class="btn btn-default">month</button>
						<button id="year" type="button" class="btn btn-default">year</button>
					</div>
					<xsl:text> </xsl:text>
					<div class="btn-group" role="group" id="fb">
						<button id="back" type="button" class="btn btn-default">&lt;</button>
						<button id="forward" type="button" class="btn btn-default">&gt;</button>
					</div>
	 			</div>
	 		</div>
	 		
	 		<div style="margin-top: 0.5em;">
	 			<div class="row"><label class="col-xs-3">Value</label><div class="col-xs-9"><b><xsl:value-of select="valueHuman"/><xsl:text> </xsl:text><xsl:value-of select="defn/units"/></b></div></div>
	 			<div class="row"><label class="col-xs-3">Last Modified</label><div class="col-xs-9"><xsl:value-of select="lastModifiedHuman"/></div></div>
	 			<div class="row"><label class="col-xs-3">Last Updated</label><div class="col-xs-9"><xsl:value-of select="lastUpdatedHuman"/></div></div>
	 			
	 			<xsl:choose>
		 			<xsl:when test="string-length(error)">
		 				<div class="row">
		 					<label class="col-xs-3">Current Error</label>
		 					<div class="col-xs-9">
		 						<div ><xsl:value-of select="error"/></div>
		 						<div>Raised <xsl:value-of select="errors/lastErrorDuration"/> ago</div>
		 					</div>
	 					</div>
		 			</xsl:when>
		 			<xsl:when test="errors/errorCount=0">
		 				<div class="row">
		 					<label class="col-xs-3">Last Error</label>
		 					<div class="col-xs-9">
		 						<div>None</div>
		 					</div>
	 					</div>
		 			</xsl:when>
		 			<xsl:otherwise>
		 				<div class="row">
		 					<label class="col-xs-3">Last Error</label>
		 					<div class="col-xs-9">
		 						<div ><xsl:value-of select="errors/lastError"/></div>
		 						<div>Cleared <xsl:value-of select="errors/lastErrorCleared"/> ago</div>
		 					</div>
	 					</div>
		 			</xsl:otherwise>
		 		</xsl:choose>	
	 			<div class="row">
	 				<label class="col-xs-3">Error Statistics (3month)</label>
	 			
	 				<div class="col-xs-9">
	 					<div>Elapsed time <xsl:value-of select="errors/errorDuration"/></div>
	 					<div>Percent time <xsl:value-of select="format-number(errors/errorPercent,'00.000%')"/></div>
	 					<div>Error count <xsl:value-of select="errors/errorCount"/></div>
	 				</div>
	 			</div>
	 			
	 			<div class="row"><hr/></div>
	 			<div class="row"><label class="col-xs-3">Constraints</label><div class="col-xs-9">
	 				<xsl:choose><xsl:when test="constraints/constraint">
	 				<xsl:for-each select="constraints/constraint">
						<xsl:text> </xsl:text>
						<span class="label label-danger">
							<xsl:value-of select="name" /><xsl:text> </xsl:text>(<xsl:value-of select="value" />)
						</span>
					</xsl:for-each>
					</xsl:when><xsl:otherwise>None</xsl:otherwise></xsl:choose>
	 			</div></div>
	 			<div class="row"><label class="col-xs-3">Keywords</label><div class="col-xs-9">
	 				<xsl:choose><xsl:when test="keywordList/keyword">
					<xsl:for-each select="keywordList/keyword">
						<xsl:text> </xsl:text>
						<span class="label label-info">
							<xsl:value-of select="." />
						</span>
					</xsl:for-each>
					</xsl:when><xsl:otherwise>None</xsl:otherwise></xsl:choose>
	 			</div></div>
	 			<div class="row"><hr/></div>
	 			<div class="row"><label class="col-xs-3">First Data</label><div class="col-xs-9"><xsl:value-of select="info/first"/></div></div>
	 			<div class="row"><label class="col-xs-3">Record Count</label><div class="col-xs-9"><xsl:value-of select="info/count"/></div></div>
	 			<div class="row"><label class="col-xs-3">Record Size</label><div class="col-xs-9"><xsl:value-of select="info/avgSize"/> bytes</div></div>
	 			<div class="row"><label class="col-xs-3">Total Size</label><div class="col-xs-9"><xsl:value-of select="format-number((info/size) div 1024,'#.0')"/> Kb</div></div>
	 		</div>
 			
		</div>
    </xsl:template>
    
    <xsl:template name="js">
		<script type="text/javascript" src="/polestar/pub/protovis-d3.2.js"></script>
    		<script><xsl:comment>
    		
    		<xsl:choose>
    			<xsl:when test="defn/chart-period">
    		var period="<xsl:value-of select="defn/chart-period"/>";
    			</xsl:when>
    			<xsl:otherwise>
    		var period="day";
    			</xsl:otherwise>
    		</xsl:choose>
    		var offset=0;
    		
    		$(document).ready(function() {
    		
    			
    		
    			setupButtons();
    			updateButtons();
    			renderChart();
    		});
    		
    		function updateButtons()
    		{	$("#periods").find("button").removeClass("active");
    			$("#periods").find("#"+period).addClass("active");
    		}
    		
    		function setupButtons()
    		{	$("#periods").find("button").click( function() {
    				period=this.id;
    				offset=0;
    				updateButtons();
    				renderChart();
    			});
    			$("#back").click( function () {
    				offset=offset-1;
    				renderChart();
    			});
    			$("#forward").click( function () {
    				if (0>offset) {
    					offset=offset+1;
    					renderChart();
    				}	
    			});
    		}
    		
    	function renderChart()
		{
			var val= {};
			val.width=$(window).width();
			val.width2 = document.getElementById('dataChart').offsetWidth-32;
			val.offset=offset;
			val.period=period;
			
			var jsonData=JSON.stringify(val, null, "  ");
			//console.log(jsonData);
			var webId="<xsl:value-of select="webId"/>";
			$.post("/polestar/sensors/detailChart/"+webId,jsonData,function(d) {
				$("#dataChart").html(d);
			});
			$.post("/polestar/sensors/errorChart/"+webId,jsonData,function(d) {
				$("#errorChart").html(d);
			});
		}
		
    		</xsl:comment></script>
    </xsl:template>
    
</xsl:stylesheet>