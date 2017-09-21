var editor;
var fragments = [];
var _tpls = [];
file_mode = "HTML";
	$(document).ready(function() {
		var mixedMode = {
		        name: "htmlmixed",
		        scriptTypes: [{matches: /\/x-handlebars-template|\/x-mustache/i,
		                       mode: null},
		                      {matches: /(text|application)\/(x-)?vb(a|script)/i,
		                       mode: "vbscript"}]
		      };
	    editor = CodeMirror.fromTextArea(document.getElementById("editor"), {
	        mode: mixedMode,
	        lineNumbers: true,
	        autoCloseTags: true,
	        extraKeys: {"Alt-/": "autocomplete"}
	      });
	    editor.setSize('100%',$(window).height() * 0.8);
		$("#loadingModal").on("hidden.bs.modal", function() {
			clearTip();
			$("#loadingModal img").show();
		})
		$("#grabModal").on("hidden.bs.modal", function() {
			$("#loadingModal img").show();
		}).on("show.bs.modal", function() {
			$("#grab_url").val("")
		});
		$("#lookupModal").on("show.bs.modal", function() {
			showDataTags();
		});
		$('[data-handler]').click(function(){
			var m = $(this).attr("data-handler");
			switch(m){
			case 'file':
				fileSelectPageQuery(1,'');
	        	$("#fileSelectModal").modal("show");
				break;
			case 'format':
			      CodeMirror.commands["selectAll"](editor);
			      autoFormatSelection()
				break;
			case 'lookup':
				lookup();
				break;
			case 'lock':
				showLock();
				break;
			default:
				break;
			}
		})
		$('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
			var html = '';
			var id = $(e.target).attr('id');
			switch(id){
			case "data-tab":
				showDataTags();
				break;
			case "fragment-tab":
				showUserFragment(1)
				break;
			}
		});
		
	});
	 $(document).keydown(function(e) {
	    if (e.ctrlKey && e.shiftKey && e.which === 70) {
	        autoFormatSelection()
	        return false;
	    }
	    return true;
	 });
	function getSelectedRange() {
	    return { from: editor.getCursor(true), to: editor.getCursor(false) };
	  }
	  
	  function autoFormatSelection() {
	    var range = getSelectedRange();
	    editor.autoFormatRange(range.from, range.to);
	  }
	
	function addDataTag(name){
		editor.replaceSelection('<data name="'+name+'"/>');
		$("#lookupModal").modal('hide');
	}
	function addFragment(name){
		editor.replaceSelection('<fragment name="'+name+'"/>');
		$("#lookupModal").modal('hide')
	}
	
	function showLock(){
		$.get(basePath + '/mgr/lock/all',{},function(data){
			var oldLock = $("#oldLock").val();
			if(data.success){
				var locks = data.data;
				var html = '';
				if(locks.length > 0){
					html += '<div class="table-responsive">';
					html += '<table class="table">';
					for(var i=0;i<locks.length;i++){
						html += '<tr>';
						var lock = locks[i];
						html += '<tr>';
						html += '<td>'+lock.name+'</td>';
						html += '<td><a href="###" onclick="addLock(\''+lock.id+'\')"><span class="glyphicon glyphicon-ok-sign"></span></a></td>';
						html += '</tr>';
					}
					html += '</table>';
					html += '</div>';
					$("#lockBody").html(html);
					$("#lockModal").modal('show')
				} else {
					bootbox.alert("当前没有任何锁");
				}
			}else{
				console.log(data.data);
			}
		});
	}
	
	function addLock(id){
		editor.replaceSelection('<lock id="'+id+'"/>');
		$("#lockModal").modal('hide')
	}
	
	function lookup(){
		$("#lookupModal").modal('show');
	}
	
	function showDataTags(){
		var html = '';
		$('[aria-labelledby="data-tab"]').html('<img src="'+basePath+'/static/img/loading.gif" class="img-responsive center-block"/>')
		$.get(basePath+"/mgr/template/dataTags",{},function(data){
			if(!data.success){
				bootbox.alert(data.message);
				return ;
			}
			data = data.data;
			html += '<div class=" table-responsive" style="margin-top:10px">';
			html += '<table class="table">';
			for(var i=0;i<data.length;i++){
				html += '<tr>';
				html += '<td>'+data[i]+'</td>';
				html += '<td><a onclick="addDataTag(\''+data[i]+'\')" href="###"><span class="glyphicon glyphicon-ok-sign" ></span>&nbsp;</a></td>';
				html += '</tr>';
			}
			html += '</table>';
			html += '</div>';
			$('[aria-labelledby="data-tab"]').html(html);
		});
	}
	
	function showUserFragment(i){
		var html = '';
		$('#fragment').html('<img src="'+basePath+'/static/img/loading.gif" class="img-responsive center-block"/>')
		$.get(basePath+"/mgr/template/fragment/list",{"currentPage":i},function(data){
			if(!data.success){
				bootbox.alert(data.message);
				return ;
			}
			var page = data.data;
			html += '<div class=" table-responsive" style="margin-top:10px">';
			html += '<table class="table">';
			for(var i=0;i<page.datas.length;i++){
				html += '<tr>';
				html += '<td>'+page.datas[i].name+'</td>';
				html += '<td><a onclick="addFragment(\''+page.datas[i].name+'\')" href="###"><span class="glyphicon glyphicon-ok-sign" ></span>&nbsp;</a></td>';
				html += '</tr>';
			}
			html += '</table>';
			html += '</div>';
			
			if(page.totalPage > 1){
				html += '<div>';
				html += '<ul class="pagination">';
				for(var i=page.listbegin;i<=page.listend-1;i++){
					html += '<li>';
					html += '<a href="###" onclick="showUserFragment(\''+i+'\')" >'+i+'</a>';
					html += '</li>';
				}
				html += '</ul>';
				html += '</div>';
			}
			$('#fragment').html(html);
		});
	}
	function showError(data){
		if(data.message){
			bootbox.alert(data.message);
			return ;
		}
		data = data.data;
		var infos = data.templateErrorInfos;
		var html = '<div class="table-responsive">';
		html += '<table class="table">';
		html += '<tr>';
		html += '<th>模板名</th>';
		html += '<th>行号</th>';
		html += '<th>列号</th>';
		html += '</tr>';
		for(var i=0;i<infos.length;i++){
			var info = infos[i];
			html += '<tr >';
			html += '<td >'+info.templateName+'</td>';
			html += '<td>'+(info.line ? info.line : '')+'</td>';
			html += '<td>'+(info.col ? info.col : '')+'</td>';
			html += '</tr>';
		}
		html += '</table>';
		html += '</div>';
		if(data.expression){
			html += '<p>表达式:<span >'+data.expression+'</span></p>'
		}
		bootbox.dialog({
			title : '渲染异常',
			message : html,
			buttons : {
				success : {
					label : "确定",
					className : "btn-success"
				}
			}
		});
	}