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
    
    <xsl:template match="/">
    	<div class="container top">
    	
    		<div class="row">
    			<button class="btn btn-default" onclick="regenerateSensorState()">regenerate current sensor state</button>
    		</div>
    	
    	
    		<div class="row">
		    	<table class="table table-hover condensed table-responsive">
		    		<thead>
		    			<tr>
		    				<th>Sensor</th>
		    				<th>Records</th>
		    				<th>Storage</th>
		    				<th>Avg Storage</th>
		    				<th>First</th>
		    				<th>Last</th>
		    				<th></th>
		    			</tr>
		    		</thead>
		    		<tbody>
		    			<xsl:for-each select="/sensors/sensor">
		    				<tr>
		    					<td><xsl:value-of select="id"/></td>
		    					<td><xsl:value-of select="count"/></td>
		    					<td><xsl:value-of select="format-number( size div (1024*1024), '0.0')"/>Mb</td>
		    					<td><xsl:value-of select="avgSize"/>b</td>
		    					<td><xsl:value-of select="first"/></td>
		    					<td><xsl:value-of select="last"/></td>
		    					<td><button class="btn btn-danger" onclick="deleteSensor('{id}')">delete</button></td>
		    				</tr>
		    			</xsl:for-each>
		    		</tbody>
		    	</table>
		    </div>
		    <script><xsl:comment>
		    var toDelete;
			function deleteSensor(id) {
				toDelete=id;
				$('#toDelete').text(id);
				console.log(id);
				$('#confirmDeleteDialog').modal({
        			show: true
    			});
			}
			function doDelete() {
				location.href="/polestar/sensorinfo?delete="+toDelete;
			}
			
			function regenerateSensorState() {
				location.href="/polestar/sensorinfo?regenerate";
			}
			
			</xsl:comment></script>
			<div class="modal" id="confirmDeleteDialog" tabindex="-1" role="dialog" aria-hidden="true">
				<div class="modal-dialog">
    				<div class="modal-content">
    					<div class="modal-body">
    						<div class="alert alert-danger">
    							<p>Please confirm you really want to delete sensor data for <span style="font-weight: bold" id="toDelete"/></p> 
    						</div>
						</div>
						<div class="modal-footer">
							<button type="button" class="btn btn-default" data-dismiss="modal">Cancel</button>
							<button type="button" class="btn btn-danger" data-dismiss="modal" onclick="javascript:doDelete()">Delete</button>
						</div>
					</div>
    			</div>
    		</div>
		</div>
		
    </xsl:template>
    
</xsl:stylesheet>