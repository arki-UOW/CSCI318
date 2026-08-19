package au.edu.uow.csci318.subject.infrastructure;

import au.edu.uow.csci318.subject.dto.SubjectDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

public interface OutlineExtraction { ExtractionResult extract(String text); }

@Component
class PdfTextExtractor {
 String extract(byte[] bytes){try(var document=Loader.loadPDF(bytes)){return new PDFTextStripper().getText(document);}catch(IOException e){throw new IllegalArgumentException("The PDF could not be read",e);}}
}

@Component
class SafeOutlineExtractor implements OutlineExtraction {
 private final ObjectMapper json; private final String apiKey; private final String model;
 SafeOutlineExtractor(ObjectMapper json,@Value("${study.ai.api-key:}") String apiKey,@Value("${study.ai.model:gpt-4.1-mini}") String model){this.json=json;this.apiKey=apiKey;this.model=model;}
 public ExtractionResult extract(String text){
   if(!apiKey.isBlank()) try{return ai(text);}catch(Exception ignored){}
   return deterministic(text);
 }
 private ExtractionResult ai(String text)throws Exception{
   Class<?> c=Class.forName("dev.langchain4j.model.openai.OpenAiChatModel"); Object builder=c.getMethod("builder").invoke(null);
   builder.getClass().getMethod("apiKey",String.class).invoke(builder,apiKey); builder.getClass().getMethod("modelName",String.class).invoke(builder,model); Object chat=builder.getClass().getMethod("build").invoke(builder);
   String prompt="Extract only facts from this subject outline. Return JSON with subjectCode, subjectName, creditPoints, assessments [{title,type,weighting,dueDate,dueWeek,description,estimatedHours,confidence,warning}], warnings. Use null for missing values; never invent dates. OUTLINE:\n"+text.substring(0,Math.min(text.length(),50000));
   Method method=Arrays.stream(chat.getClass().getMethods()).filter(m->m.getName().equals("chat")&&m.getParameterCount()==1&&m.getParameterTypes()[0]==String.class).findFirst().orElseThrow();
   String raw=(String)method.invoke(chat,prompt); raw=raw.replaceFirst("(?s)^```(?:json)?\\s*","").replaceFirst("(?s)\\s*```$","");
   return validate(json.readValue(raw,ExtractionResult.class));
 }
 private ExtractionResult deterministic(String text){
   Matcher code=Pattern.compile("\\b([A-Z]{2,8}\\s?[0-9]{3,4})\\b").matcher(text.toUpperCase()); String subjectCode=code.find()?code.group(1).replace(" ",""):null;
   String subjectName=null; for(String line:text.lines().map(String::trim).toList()) if(subjectCode!=null&&line.toUpperCase().contains(subjectCode)&&line.length()>subjectCode.length()){subjectName=line.replaceAll("(?i).*"+subjectCode+"\\s*[-:–]?\\s*","").trim();break;}
   List<AssessmentCandidate> items=new ArrayList<>(); Pattern p=Pattern.compile("(?im)^(?:assessment\\s*(?:task)?\\s*\\d*[:.-]?\\s*)?(.{3,80}?)\\s+(\\d{1,3}(?:\\.\\d+)?)%.*?(?:due\\s*)?(\\d{1,2}/\\d{1,2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|week\\s*\\d{1,2})?$");
   Matcher m=p.matcher(text); Set<String> seen=new HashSet<>(); while(m.find()&&items.size()<20){String title=m.group(1).trim();if(!seen.add(title.toLowerCase()))continue;Double weight=Double.valueOf(m.group(2));String due=m.group(3);LocalDate date=null;Integer week=null;if(due!=null&&due.toLowerCase().startsWith("week"))week=Integer.valueOf(due.replaceAll("\\D",""));else if(due!=null)try{date=due.contains("/")?LocalDate.parse(due,java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy")):LocalDate.parse(due);}catch(Exception ignored){}items.add(new AssessmentCandidate(title,"Assessment",weight,date,week,null,null,.65,date==null&&week==null?"Due date not confidently detected":null));}
   List<String> warnings=new ArrayList<>();if(subjectCode==null)warnings.add("Subject code was not confidently detected");if(subjectName==null)warnings.add("Subject name was not confidently detected");if(items.isEmpty())warnings.add("No assessment rows were confidently detected; add them during review");
   return new ExtractionResult(subjectCode,subjectName,null,items,warnings);
 }
 private ExtractionResult validate(ExtractionResult r){if(r==null||r.subjectCode()==null||r.subjectName()==null)throw new IllegalArgumentException("AI extraction omitted required subject data");Set<String>s=new HashSet<>();for(var a:r.assessments()){if(a.title()==null||a.title().isBlank())throw new IllegalArgumentException("Assessment title is required");if(a.weighting()!=null&&(a.weighting()<0||a.weighting()>100))throw new IllegalArgumentException("Assessment weighting is invalid");if(!s.add(a.title().trim().toLowerCase()))throw new IllegalArgumentException("Duplicate assessment: "+a.title());}return r;}
}
