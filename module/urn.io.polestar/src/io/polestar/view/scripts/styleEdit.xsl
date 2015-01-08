<?xml version="1.0" encoding="UTF-8" ?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml"/>

    <xsl:template match="/*">
    	<div id="fill" class="container top" style="height: 100%">
    		<script><xsl:comment>
				
				function resize()
				{	height=$("#fill").height();
  					//console.log(height);
  					$('textarea').css('height',height-250);
				}
    		
    			$(window).resize(function()
    			{	resize();
				});
				
				$(function() {
					
					resize();
  					
					$("textarea").bind('keydown', function(e){
					      pressedKey = e.charCode || e.keyCode || -1;
					      if (pressedKey == 9) {
					        if (window.event) {
					          window.event.cancelBubble = true;
					          window.event.returnValue = false;
					        } else {
					          e.preventDefault();
					          e.stopPropagation();
					        }
					
					        // save current scroll position for later restoration
					        var oldScrollTop=this.scrollTop;
					
					        if (this.createTextRange) {
					          document.selection.createRange().text="\t";
					          this.onblur = function() { this.focus(); this.onblur = null; };
					        } else if (this.setSelectionRange) {
					          start = this.selectionStart;
					          end = this.selectionEnd;
					          this.value = this.value.substring(0, start) + "\t" + this.value.substr(end);
					          this.setSelectionRange(start + 1, start + 1);
					          this.focus();
					        }
					
					        this.scrollTop=oldScrollTop;
					
					        return false;
					      }
					    }
					  );
				});
				
				function doDelete()
				{	location.href="/polestar/scripts/delete/<xsl:value-of select='id'/>";
				}
			</xsl:comment></script>
			
			<form method="POST" class="form-horizontal">
				<div class="form-group">
					<label class="col-sm-1 control-label" for="name">Name</label>
					<div class="col-sm-11">
						<input type="text" class="form-control" name="name" value="{name}"/>
					</div>
				</div>
				<div class="form-group">
					<label class="col-sm-1 control-label" for="triggers">Triggers</label>
					<div class="col-sm-11">
						<input type="text" class="form-control" name="triggers" value="{triggers}" placeholder="comma separated list of sensors that trigger script"/>
					</div>
				</div>
				<div class="form-group">
					<label class="col-sm-1 control-label" for="keywords">Keyword</label>
					<div class="col-sm-11">
						<input type="text" class="form-control" name="keywords" value="{keywords}" placeholder="comma separated list of keywords"/>
					</div>
				</div>
				<div class="form-group">
					<label class="col-sm-1 control-label" for="name">Access</label>
					<div class="col-sm-5">
						<!--
						<input name="public" type="checkbox" >
							<xsl:if test="public='true'"><xsl:attribute name="checked"/></xsl:if>
						</input>
						-->
						<select name="public" class="form-control">
							<option>
								<xsl:if test="public='private'"><xsl:attribute name="selected"/></xsl:if>
								private</option>
							<option>
								<xsl:if test="public='guest'"><xsl:attribute name="selected"/></xsl:if>
								guest</option>
							<option>
								<xsl:if test="public='public'"><xsl:attribute name="selected"/></xsl:if>
								public</option>
						</select>
						
					</div>
				</div>
				<div class="form-group">
					<textarea class="scriptcode form-control" name="script" spellcheck="false">
						<xsl:value-of select="script"/>
					</textarea>
				</div>
				<div class="form-group">
					<button type="button" class="btn btn-danger" data-toggle="modal" data-target="#confirmDeleteDialog">
						<span class="glyphicon glyphicon-trash"></span>
						Delete
					</button>				
					<xsl:text> </xsl:text>
					<button type="submit" name="cancel" class="btn btn-default">Cancel</button>
					<xsl:text> </xsl:text>
					<button type="submit" name="save" class="btn btn-primary">Save</button>
					<xsl:text> </xsl:text>
					<button type="submit" name="execute" class="btn btn-success">
						<span class="glyphicon glyphicon-play"></span>
						Execute
					</button>
				</div>
			</form>
			
			<div class="modal" id="confirmDeleteDialog" tabindex="-1" role="dialog" aria-hidden="true">
				<div class="modal-dialog">
    				<div class="modal-content">
    					<div class="modal-body">
    						<div class="alert alert-danger">
    							<p>Please confirm you really want to delete <xsl:value-of select="name"/></p> 
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