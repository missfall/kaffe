/* gnu.classpath.tools.gjdoc.Parser
   Copyright (C) 2001 Free Software Foundation, Inc.

   This file is part of GNU Classpath.

   GNU Classpath is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2, or (at your option)
   any later version.
 
   GNU Classpath is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with GNU Classpath; see the file COPYING.  If not, write to the
   Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
   02111-1307 USA. */

package gnu.classpath.tools.gjdoc;

import java.io.*;
import java.util.*;
import com.sun.javadoc.*;

   abstract class SourceComponent {

      abstract int match(char[] source, int index) throws ParseException;

      int process(Parser parser, char[] source, int startIndex, int endIndex) throws ParseException, IOException {
	 return endIndex;
      }

      int getEndIndex(char[] source, int endIndex) throws ParseException {
	 return endIndex;
      }
   }

   abstract class BlockSourceComponent extends SourceComponent {

      int getEndIndex(char[] source, int endIndex) throws ParseException {
	 return Parser.skipExpression(source, endIndex, 1, '\0');
      }

   }

   class Whitespace extends SourceComponent {

      int match(char[] source, int index) {

	 int rc=index;
	 int slen=source.length;
	 while (rc<slen && Parser.WHITESPACE.indexOf(source[rc])>=0) ++rc;

	 return (rc!=index) ? rc : -1;
      }
   }

   class BracketClose extends SourceComponent {

      int match(char[] source, int index) {
	 if (source[index]=='}') {
	    return index+1;
	 }
	 else {
	    return -1;
	 }
      }

      int process(Parser parser, char[] source, int startIndex, int endIndex) throws ParseException, IOException {

	 parser.classClosed();
	 return endIndex;
      }
   }

   class CommentComponent extends SourceComponent {

      int match(char[] source, int index) throws ParseException {
	 if (index+1<source.length && source[index]=='/' && source[index+1]=='*') {
	    for (index+=2; index+1<source.length; ++index) {
	       if (source[index]=='*' && source[index+1]=='/')
		  return index+2;
	    }
	    throw new ParseException("unexpected end of input");
	 }
	 return -1;
      }

      int process(Parser parser, char[] source, int startIndex, int endIndex) {

	 if (source[startIndex+0]=='/' 
	     && source[startIndex+1]=='*' 
	     && source[startIndex+2]=='*') {

	    parser.setLastComment(new String(source, startIndex, endIndex-startIndex));
	 }

	 return endIndex;
      }
   }

   class SlashSlashCommentComponent extends SourceComponent {

      int match(char[] source, int index) {
	 if (index+1<source.length && source[index]=='/' && source[index+1]=='/') {
	    index+=2;
	    while (index<source.length && source[index]!='\n')
	       ++index;
	    return index;
	 }
	 else {
	    return -1;
	 }
      }
   }


   class ImportComponent extends SourceComponent {

      int match(char[] source, int index) {
	 if (index+7<source.length) {
	    if (source[index+0]=='i' 
		&& source[index+1]=='m'
		&& source[index+2]=='p'
		&& source[index+3]=='o'
		&& source[index+4]=='r'
		&& source[index+5]=='t'
		&& Parser.WHITESPACE.indexOf(source[index+6])>=0) {

	       for (index+=7; index<source.length && source[index]!=';'; ++index)
		  ;

	       return index+1;
	    }
	 }
	 return -1;
      }

      int process(Parser parser, char[] source, int startIndex, int endIndex) throws ParseException, IOException {
	 String importString=new String(source,startIndex+7,endIndex-startIndex-7-1).trim();
	 parser.importEncountered(importString);     
	 return endIndex;
      }
   }

   class PackageComponent extends SourceComponent {

      int match(char[] source, int index) {
	 if (index+10<source.length) {
	    if (source[index+0]=='p' 
		&& source[index+1]=='a'
		&& source[index+2]=='c'
		&& source[index+3]=='k'
		&& source[index+4]=='a'
		&& source[index+5]=='g'
		&& source[index+6]=='e'
		&& Parser.WHITESPACE.indexOf(source[index+7])>=0) {

	       for (index+=7; index<source.length && source[index]!=';'; ++index)
		  ;

	       return index+1;
	    }
	 }
	 return -1;
      }

      int process(Parser parser, char[] source, int startIndex, int endIndex) {
	 String packageName=new String(source,startIndex+8,endIndex-startIndex-8-1).trim();
	 parser.packageOpened(packageName);
	 return endIndex;
      }
   }

   class FieldComponent extends SourceComponent {

      int match(char[] source, int index) throws ParseException {
	 boolean isField=false;
	 final int STATE_NORMAL=1;
	 final int STATE_SLASHC=2;
	 final int STATE_STARC=3;

	 int state=STATE_NORMAL;

	 for (; index<source.length && !isField; ++index) {
	    if (state==STATE_STARC) {
	       if (index<source.length-1 && source[index]=='*' && source[index+1]=='/') {
		  ++index;
		  state=STATE_NORMAL;
	       }
	    }
	    else if (state==STATE_SLASHC) {
	       if (source[index]=='\n') {
		  state=STATE_NORMAL;
	       }
	    }
	    else switch (source[index]) {
	    case '/': 
	       if (index<source.length-1 && source[index+1]=='*') {
		  state=STATE_STARC; 
		  ++index;
	       }
	       else if (index<source.length-1 && source[index+1]=='/') {
		  state=STATE_SLASHC; 
		  ++index;
	       }
	       break;
	    case '{':  // class
	    case '(':  // method
	       return -1;
	    case '=':  // field
	    case ';':  // field
	       isField=true;
	       break;
	    }
	    if (isField) break;
	 }
	 if (!isField || index==source.length) {
	    return -1;
	 }

	 //System.err.println("char is "+source[index]);

	 if (source[index]!=';') {
	    index=Parser.skipExpression(source, index, 0, ';');
	 }
	 return index+1;
      }

      int process(Parser parser, char[] source, int startIndex, int endIndex) {

	 //Debug.log(9,"found package statement: \""+str+"\"");
	 //Debug.log(9,"found function component: '"+str+"'");
	 //xxx(new FieldDocImpl(ctx.classDoc, ctx.classDoc.containingPackage(), 0, false, false));

	 // Ignore superfluous semicoli after class definition
	 if (endIndex-startIndex<=1) return endIndex;

	 //assert (parser.ctx!=null);
	 Collection fields=FieldDocImpl.createFromSource(parser.ctx.classDoc, 
							 parser.ctx.classDoc.containingPackage(), 
							 source, startIndex, endIndex);

	 for (Iterator it=fields.iterator(); it.hasNext(); ) {
	    FieldDocImpl field=(FieldDocImpl)it.next();
	    boolean fieldHasSerialTag=!field.isTransient() && !field.isStatic(); //field.hasSerialTag();
	    if ((field.isIncluded() || fieldHasSerialTag) && parser.addComments) {
	       field.setRawCommentText(parser.getLastComment());
	    }
            parser.ctx.fieldList.add(field);
	    if (field.isIncluded()) {
	       parser.ctx.filteredFieldList.add(field);
	    }
	    if (fieldHasSerialTag) {
	       parser.ctx.sfieldList.add(field);
	    }
	 }

	 parser.setLastComment(null);
	 return endIndex;
      }
   

   }

   class FunctionComponent extends BlockSourceComponent {

      int getEndIndex(char[] source, int endIndex) throws ParseException {
	 if (source[endIndex-1]==';') {
	    return endIndex;
	 }
	 else {
	    return super.getEndIndex(source, endIndex);
	 }
      }

      int process(Parser parser, char[] source, int startIndex, int endIndex) throws IOException, ParseException {

	 //ctx.fieldList.add(FieldDocImpl.createFromSource(source, startIndex, endIndex));

	 //System.out.println("function match '"+new String(source,startIndex,endIndex-startIndex)+"'");

	 ExecutableMemberDocImpl execDoc=MethodDocImpl.createFromSource(parser.ctx.classDoc, 
									parser.ctx.classDoc.containingPackage(), 
									source, startIndex, endIndex);

	 if (parser.addComments)
	    execDoc.setRawCommentText(parser.getLastComment());

	 parser.setLastComment(null);

         if (execDoc.isMethod()) {
            parser.ctx.methodList.add(execDoc);
            if (execDoc.isIncluded()) {
               parser.ctx.filteredMethodList.add(execDoc);
            }
         }
         else {
            parser.ctx.constructorList.add(execDoc);
            if (execDoc.isIncluded()) {
               parser.ctx.filteredConstructorList.add(execDoc);
            }
         }

	 if (execDoc.isMethod() 
		  && (execDoc.name().equals("readObject")
		      || execDoc.name().equals("writeObject"))) {

	    parser.ctx.maybeSerMethodList.add(execDoc);
	 }

	 return endIndex;
      }

      int match(char[] source, int index) {
	 boolean isFunc=false;
	 final int STATE_NORMAL=1;
	 final int STATE_SLASHC=2;
	 final int STATE_STARC=3;
	 int state=STATE_NORMAL;
	 for (; index<source.length && !isFunc; ++index) {
	    if (state==STATE_STARC) {
	       if (source[index]=='*' && source[index+1]=='/') {
		  ++index;
		  state=STATE_NORMAL;
	       }
	    }
	    else if (state==STATE_SLASHC) {
	       if (source[index]=='\n') {
		  state=STATE_NORMAL;
	       }
	    }
	    else switch (source[index]) {
	    case '/': 
	       if (source[index+1]=='*') {
		  state=STATE_STARC; 
		  ++index;
	       }
	       else if (source[index+1]=='/') {
		  state=STATE_SLASHC; 
		  ++index;
	       }
	       break;
	    case '=':  // field
	    case ';':  // field
	    case '{':  // class
	       return -1;
	    case '(':
	       isFunc=true;
	       break;
	    }
	    if (isFunc) break;
	 }
	 if (!isFunc || index==source.length)
	    return -1;

	 for (; index<source.length && (state!=STATE_NORMAL || (source[index]!='{' && source[index]!=';')); ++index)
	    if (state==STATE_SLASHC && source[index]=='\n') {
	       state=STATE_NORMAL;
	    }
	    else if (index<source.length-1) {
	       if (state==STATE_STARC) {
		  if (source[index]=='*' && source[index+1]=='/') {
		     state=STATE_NORMAL;
		  }
	       }
	       else {
		  if (source[index]=='/' && source[index+1]=='*') {
		     state=STATE_STARC;
		  }
		  else if (source[index]=='/' && source[index+1]=='/') {
		     state=STATE_SLASHC;
		  }
	       }
	    }
	 return index+1;
      }
   

   }

   class StaticBlockComponent extends BlockSourceComponent {

      int process(Parser parser, char[] source, int startIndex, int endIndex) {
	 //Debug.log(9,"found package statement: \""+str+"\"");
	 //Debug.log(9,"found function component: '"+str+"'");
	 parser.setLastComment(null);
	 return endIndex;
      }
   
      int match(char[] source, int index) {
	 if (source[index]=='{') return index+1;

	 if (index+7<source.length) {
	    if (source[index+0]=='s' 
		&& source[index+1]=='t'
		&& source[index+2]=='a'
		&& source[index+3]=='t'
		&& source[index+4]=='i'
		&& source[index+5]=='c') {

	       for (index+=6; index<source.length && Parser.WHITESPACE.indexOf(source[index])>=0; ++index)
		  ;

	       if (index<source.length && source[index]=='{')
		  return index+1;
	       else
		  return -1;
	    }
	 }
	 return -1;
      }

   }

   class ClassComponent extends SourceComponent {

      int match(char[] source, int index) {
	 boolean isClass=false;
	 for (; index<source.length && !isClass; ++index) {
	    switch (source[index]) {
            case '/':  // possible comment
               if (index<source.length-1) {
                  char c = source[index+1];
                  if ('/' == c) {
                     index += 2;
                     while (index<source.length && source[index]!=10) {
                        ++ index;
                     }
                  }
                  else if ('*' == c) {
                     index += 3;
                     while (index<source.length && (source[index-1] != '*' || source[index]!='/')) {
                        ++ index;
                     }
                  }
               }
               break;
	    case '=':  // field
	    case ';':  // field
	    case '(':  // function
	       return -1;
	    case '{':
	       isClass=true;
	       break;
	    }
	    if (isClass) break;
	 }
	 if (!isClass || index>=source.length)
	    return -1;

	 return index+1;
      }

      int process(Parser parser, char[] source, int startIndex, int endIndex) throws ParseException, IOException {

	 parser.classOpened(source, startIndex, endIndex);
	 if (parser.addComments)
	    parser.ctx.classDoc.setRawCommentText(parser.getLastComment());
	 parser.setLastComment(null);

	 int rc=parser.parse(source, endIndex, parser.classLevelComponents);
	 return rc;
      }

   }


public class Parser {


   static int skipExpression(char[] source, int endIndex, int level, char delimiter) throws ParseException {

      int orgEndIndex=endIndex;

      final int STATE_NORMAL=1;
      final int STATE_STARC=2;
      final int STATE_SLASHC=3;
      final int STATE_CHAR=4;
      final int STATE_STRING=5;

      int state=STATE_NORMAL;
      int prev=0;
      for (; !((level==0 && state==STATE_NORMAL && (delimiter=='\0' || source[endIndex]==delimiter))) && endIndex<source.length; ++endIndex) {
	 int c=source[endIndex];
	 if (state==STATE_NORMAL) {
	    if (c=='}') --level;
	    else if (c=='{') ++level;
	    else if (c=='/' && prev=='/') { state=STATE_SLASHC; c=0; }
	    else if (c=='*' && prev=='/') { state=STATE_STARC; c=0; }
	    else if (c=='\'' && prev!='\\') { state=STATE_CHAR; c=0; }
	    else if (c=='\"' && prev!='\\') { state=STATE_STRING; c=0; }
	 }
	 else if (state==STATE_SLASHC) {
	    if (c=='\n') state=STATE_NORMAL;
	 }
	 else if (state==STATE_CHAR) {
	    if (c=='\'' && prev!='\\') state=STATE_NORMAL;
	    else if (c=='\\' && prev=='\\') c=0;
	 }
	 else if (state==STATE_STRING) {
	    if (c=='\"' && prev!='\\') state=STATE_NORMAL;
	    else if (c=='\\' && prev=='\\') c=0;
	 }
	 else {
	    if (c=='/' && prev=='*') { state=STATE_NORMAL; c=0; }
	 }
	 prev=c;
      }
      if (level>0)
	 throw new ParseException("Unexpected end of source.");
      else {
	 String rc=new String(source, orgEndIndex, endIndex-orgEndIndex);
	 return endIndex;
      }
   }

   static boolean addComments=false;

   public static final String WHITESPACE=" \t\r\n";

   public static final boolean isWhitespace(char c) { return WHITESPACE.indexOf(c)>=0; }

   private int currentLine;

   private static final int READ_BUFFER_SIZE = 512;

   static char[] loadFile(File file, String encoding) throws IOException {
      StringWriter writer = new StringWriter();
      FileInputStream in = new FileInputStream(file);
      Reader reader;
      if (null != encoding) {
         reader = new InputStreamReader(in, encoding);
      }
      else {
         reader = new InputStreamReader(in);
      }
      char[] buffer = new char[READ_BUFFER_SIZE];
      int nread;
      while ((nread=reader.read(buffer))>=0) {
	 writer.write(buffer,0,nread);
      }
      return writer.toString().toCharArray();
   }

   static SourceComponent[] sourceLevelComponents;
   static SourceComponent[] classLevelComponents;

   public Parser() {
      try {

	 sourceLevelComponents=new SourceComponent[] {
	    new Whitespace(),
	    new CommentComponent(),
	    new SlashSlashCommentComponent(),
	    new PackageComponent(),
	    new ImportComponent(),
	    new ClassComponent(),
	 };

	 classLevelComponents=new SourceComponent[] {
	    new Whitespace(),
	    new BracketClose(),
	    new CommentComponent(),
	    new SlashSlashCommentComponent(),
	    new FunctionComponent(),
	    new StaticBlockComponent(),
	    new ImportComponent(),
	    new ClassComponent(),
	    new FieldComponent(),
	 };
      }
      catch (Exception e) {
	 e.printStackTrace();
      }
   }

   public static int getNumberOfProcessedFiles() {
      return processedFiles.size();
   }

   static Set processedFiles = new HashSet();

   ClassDocImpl processSourceFile(File file, boolean addComments, String encoding) 
      throws IOException, ParseException
   {
      this.currentPackage = PackageDocImpl.DEFAULT_PACKAGE;
      this.outerClass = null;

      this.addComments=addComments;

      if (processedFiles.contains(file)) {
         return null;
      }
      processedFiles.add(file);

      Debug.log(1,"Processing file "+file);

      contextStack.clear();
      ctx=null;

      importedClassesList.clear();
      importedStringList.clear();
      importedPackagesList.clear();
     
      currentLine = 1;

      char[] source = loadFile(file, encoding);
      parse(source, 0, sourceLevelComponents);

      ClassDoc[] importedClasses=(ClassDoc[])importedClassesList.toArray(new ClassDoc[0]);
      PackageDoc[] importedPackages=(PackageDoc[])importedPackagesList.toArray(new PackageDoc[0]);

      if (Main.DESCEND_IMPORTED) {
	 for (int i=0; i<importedClasses.length; ++i) {
	    Main.getRootDoc().scheduleClass(currentClass, importedClasses[i].qualifiedName());
	 }
      }

      /*
	if (contextStack.size()>0) {
	Debug.log(1,"-->contextStack not empty! size is "+contextStack.size());
	}
      */

      return outerClass;
   }
      
   int parse(char[] source, int index, SourceComponent[] componentTypes) throws ParseException, IOException {

      while (index<source.length) {

	 int match=-1;
	 int i=0;
	 for (; i<componentTypes.length; ++i) {
	    if ((match=componentTypes[i].match(source, index))>=0) {
	       //Debug.log(9,componentTypes[i].getClass().getName()+" ("+match+"/"+source.length+")");
	       break;
	    }
	 }

	 if (i<componentTypes.length) {
	    int endIndex=componentTypes[i].getEndIndex(source, match);
	    
	    index=componentTypes[i].process(this, source, index, endIndex);
	    if (index<0) {
	       //Debug.log(9,"exiting parse because of "+componentTypes[i].getClass().getName()+" (\""+new String(source, index, endIndex-index)+"\")");
	       return endIndex;
	    }
	 }
	 else {
	    //Debug.log(9,"index="+index+", source.length()="+source.length);
	    throw new ParseException("unmatched input in line "+currentLine+": "+new String(source, index, Math.min(50,source.length-index)));
	 }

      }
      //Debug.log(9,"exiting parse normally, index="+index+" source.length="+source.length);
      return index;
   }

   private static int countNewLines(String source) {
      int i=0;
      int rc=0;
      while ((i=source.indexOf('\n',i)+1)>0) 
	 ++rc;
      return rc;
   }

   public void processSourceDir(File dir, String encoding) throws IOException, ParseException {
      Debug.log(9,"Processing "+dir.getParentFile().getName()+"."+dir.getName());
      File[] files=dir.listFiles();
      if (null!=files) {
	 for (int i=0; i<files.length; ++i) {
	    if (files[i].getName().toLowerCase().endsWith(".java")) {
	       processSourceFile(files[i], true, encoding);
	    }
	 }
      }
   }

   void classOpened(char[] source, int startIndex, int endIndex) throws ParseException, IOException {

      referencedClassesList.clear();

      ClassDocImpl classDoc
	 = ClassDocImpl.createInstance((ctx!=null)?(ctx.classDoc):null, currentPackage, 
				       null,
				       (PackageDoc[])importedPackagesList.toArray(new PackageDoc[0]),
				       source, startIndex, endIndex);

      if (ctx!=null && classDoc.isIncluded()) ctx.innerClassesList.add(classDoc);

      if (importedClassesList.isEmpty()) {
	 for (Iterator it=importedStringList.iterator(); it.hasNext(); ) {
	    importedClassesList.add(new ClassDocProxy((String)it.next(), classDoc));
	 }
      }
      classDoc.setImportedClasses((ClassDoc[])importedClassesList.toArray(new ClassDoc[0]));


      currentPackage.addClass(classDoc);

      currentClass = classDoc;

      if (null == outerClass) {
         outerClass = classDoc;
      }

      if (classDoc.superclass()!=null)
	 referencedClassesList.add(classDoc.superclass());

      //Debug.log(9,"classOpened "+classDoc+", adding superclass "+classDoc.superclass());

      contextStack.push(ctx);
      ctx=new Context(classDoc);
      //Debug.log(9,"ctx="+ctx);
   }

   private Doc[] toSortedArray(List list, Doc[] template)
   {
      Doc[] result = (Doc[])list.toArray(template);
      Arrays.sort(result);
      return result;
   }

   void classClosed() throws ParseException, IOException {

      ctx.classDoc.setFields((FieldDoc[])toSortedArray(ctx.fieldList, 
                                                             new FieldDoc[0]));
      ctx.classDoc.setFilteredFields((FieldDoc[])toSortedArray(ctx.filteredFieldList, 
                                                                     new FieldDoc[0]));
      ctx.classDoc.setSerializableFields((FieldDoc[])toSortedArray(ctx.sfieldList, new FieldDoc[0]));
      ctx.classDoc.setMethods((MethodDoc[])toSortedArray(ctx.methodList, new MethodDoc[0]));
      ctx.classDoc.setFilteredMethods((MethodDoc[])toSortedArray(ctx.filteredMethodList, new MethodDoc[0]));
      ctx.classDoc.setMaybeSerMethodList(ctx.maybeSerMethodList);
      ctx.classDoc.setConstructors((ConstructorDoc[])toSortedArray(ctx.constructorList, new ConstructorDoc[0]));
      ctx.classDoc.setFilteredConstructors((ConstructorDoc[])toSortedArray(ctx.filteredConstructorList, new ConstructorDoc[0]));

      ctx.classDoc.setInnerClasses((ClassDocImpl[])toSortedArray(ctx.innerClassesList, new ClassDocImpl[0]));
      
      Main.getRootDoc().addClassDoc(ctx.classDoc);
      
      if (Main.DESCEND_INTERFACES) {
	 for (int i=0; i<ctx.classDoc.interfaces().length; ++i) {
	    Main.getRootDoc().scheduleClass(ctx.classDoc, ctx.classDoc.interfaces()[i].qualifiedName());
	 }
      }

      //Debug.log(9,"classClosed: "+ctx.classDoc);

      ctx=(Context)contextStack.pop();

      ClassDoc[] referencedClasses=(ClassDoc[])referencedClassesList.toArray(new ClassDoc[0]);

      if (Main.DESCEND_SUPERCLASS) {
	 for (int i=0; i<referencedClasses.length; ++i) {
	    Main.getRootDoc().scheduleClass(currentClass, referencedClasses[i].qualifiedName());
	 }
      }
   }
   
   Context      ctx             = null;
   Stack        contextStack    = new Stack();
   class Context {
      Context(ClassDocImpl classDoc) { this.classDoc=classDoc; }
      ClassDocImpl      classDoc                 = null;
      List	        fieldList                = new LinkedList();
      List	        filteredFieldList        = new LinkedList();
      List	        sfieldList               = new LinkedList();
      List	        methodList               = new LinkedList();
      List	        filteredMethodList       = new LinkedList();
      List              maybeSerMethodList       = new LinkedList();
      List	        constructorList          = new LinkedList();
      List	        filteredConstructorList  = new LinkedList();
      List              innerClassesList         = new LinkedList();
   }
   
   String lastComment = null;
   PackageDocImpl currentPackage = PackageDocImpl.DEFAULT_PACKAGE;
   ClassDocImpl currentClass = null;
   ClassDocImpl outerClass   = null;
   List ordinaryClassesList  = new LinkedList();
   List allClassesList       = new LinkedList();
   List interfacesList       = new LinkedList();

   List importedClassesList  = new LinkedList();
   List importedStringList   = new LinkedList();
   List importedPackagesList = new LinkedList();

   List referencedClassesList = new LinkedList();

   void packageOpened(String packageName) {
      currentPackage=Main.getRootDoc().findOrCreatePackageDoc(packageName);
   }
   
   void importEncountered(String importString) throws ParseException, IOException {
      //Debug.log(9,"importing '"+importString+"'");
      if (importString.endsWith(".*")) {
	 importedPackagesList.add(Main.getRootDoc().findOrCreatePackageDoc(importString.substring(0,importString.length()-2)));
      }
      else {
	 importedStringList.add(importString);
      }
   }


   void setLastComment(String lastComment) {
      this.lastComment=lastComment;
   }

   String getLastComment() {
      return this.lastComment;
   }

   public void finalize() throws Throwable {
      super.finalize();
   }
}
