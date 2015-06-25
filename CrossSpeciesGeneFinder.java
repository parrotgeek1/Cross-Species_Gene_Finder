import java.net.*;
import java.io.*;
import java.util.*;
import javax.sound.sampled.*;
import java.awt.*;
import javax.swing.*;

public class CrossSpeciesGeneFinder {
  public static class JTextAreaOutputStream extends OutputStream
  {
    private final JTextArea destination;
    
    public JTextAreaOutputStream (JTextArea destination)
    {
      if (destination == null)
        throw new IllegalArgumentException ("Destination is null");
      
      this.destination = destination;
    }
    
    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException
    {
      final String text = new String (buffer, offset, length);
      SwingUtilities.invokeLater(new Runnable ()
                                   {
        @Override
        public void run() 
        {
          destination.append (text);
        }
      });
    }
    
    @Override
    public void write(int b) throws IOException
    {
      write (new byte [] {(byte)b}, 0, 1);
    }
  }
  static PrintStream o = null;
  public static void main(String[] args){
    try {
      // Set System L&F
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } 
    catch (Exception e) {
      // oh well.
    }
    
    JTextArea textArea = new JTextArea (25, 80);
    
    textArea.setEditable (false);
    
    JFrame frame = new JFrame ("Cross-Species Gene Finder");
    frame.setDefaultCloseOperation (JFrame.EXIT_ON_CLOSE);
    Container contentPane = frame.getContentPane ();
    contentPane.setLayout (new BorderLayout ());
    contentPane.add (
                     new JScrollPane (
                                      textArea, 
                                      JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, 
                                      JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED),
                     BorderLayout.CENTER);
    frame.pack ();
    //  frame.setLocationRelativeTo(null); // center it. does not work with 2 monitors
    frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH); // instead, maximize
    frame.setVisible (true);
    
    JTextAreaOutputStream out = new JTextAreaOutputStream (textArea);
    System.setOut (new PrintStream (out));
    o = System.out;
    String species = null;
    String evalueStr = null;
    String buffStr = null;
    boolean batch = false;
    o.println("Cross-Species Gene Finder for NCBI\nBy Ethan Nelson-Moore, (C) "+Calendar.getInstance().get(Calendar.YEAR));
    o.println("It will play a \"ta-da!\" sound when it's done, or an error sound if there are any errors.\n");
    ArrayList<String> queryList = new ArrayList<String>();
    int option = JOptionPane.showConfirmDialog(null, "Do you want to use batch mode?", "Batch Mode", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
    if (option == JOptionPane.YES_OPTION) { // batch
      batch = true;
      o.print("Using batch mode. ");
      FileDialog fd = new FileDialog((Frame)null, "Choose accession number list file", FileDialog.LOAD);
      fd.setFile("*.txt");
      fd.setVisible(true);
      String filename = fd.getFile();
      if (filename == null || filename.equals("")) {
        System.exit(0);
        return;
      } 
      o.println("Accession number list file: " + filename);
      try {
        Scanner scQL = new Scanner(new File(filename));
        String line1 = scQL.nextLine();
        if(line1 != null && line1.startsWith("!CSGFBatchV1")) {
          if(line1.contains(":") && (line1.split(":").length == 3) || line1.split(":").length == 4) {
            species = line1.split(":")[1].trim();
            evalueStr = line1.split(":")[2].trim();
            if(line1.split(":").length == 4) buffStr = line1.split(":")[3].trim();
          }
          while(scQL.hasNextLine()) {
            String line = scQL.nextLine().trim();
            if(line.contains("#")) line = line.split("#")[0].trim();
            if(line.equals("")) continue;
            queryList.add(line);
          }
        } else {
          scQL.close();
          JOptionPane.showMessageDialog(null, "Not a CSGF batch file!", "CSGF Error", JOptionPane.ERROR_MESSAGE);
          System.exit(0);
          return;
        }
        scQL.close();
      } catch(IOException e) {
        JOptionPane.showMessageDialog(null, "Error loading file!"+e.getMessage(), "CSGF Error", JOptionPane.ERROR_MESSAGE);
        System.exit(0);
        return;
      }
    } else if(option == JOptionPane.NO_OPTION) {
      String input = JOptionPane.showInputDialog(null, "Enter the NCBI protein accession number for the gene you want to find.", "CSGF Input", JOptionPane.QUESTION_MESSAGE);
      if(input == null || input.equals("")) {
        System.exit(0);
        return;
      }
      queryList.add(input.trim());
    } else {
      System.exit(0);
      return;
    }
    if(species == null || species.equals("")) species = JOptionPane.showInputDialog(null, "Enter the species you want to search for the gene in.", "CSGF Input", JOptionPane.QUESTION_MESSAGE).trim();
    if(species == null || species.equals("")) {
      System.exit(0);
      return;
    }
    double maxEvalue = 0;
    try {
      if(evalueStr != null) {
        maxEvalue = Double.parseDouble(evalueStr);
      } else {
        maxEvalue = Double.parseDouble(JOptionPane.showInputDialog(null, "Enter the maximum evalue to show results for.\n(Example: 1e-30)", "CSGF Input", JOptionPane.QUESTION_MESSAGE).trim());
      }
    } catch (NumberFormatException e) {
      JOptionPane.showMessageDialog(null, "You didn't enter a valid number for the evalue!", "CSGF Error", JOptionPane.ERROR_MESSAGE);
      System.exit(0);
      return;
    } catch(NullPointerException e) {
      System.exit(0);
      return;
    }
    long bufferLeft = 1000;
    long bufferRight = 1000;
    try {
      if(buffStr == null) {
        buffStr = JOptionPane.showInputDialog(null, "Enter the maximum buffer to save on either side of the match.\n(Example: 1000 or 2000,5000)", "CSGF Input", JOptionPane.QUESTION_MESSAGE).trim();
      }
      if(buffStr.contains(",")) {
        bufferLeft = Long.parseLong(buffStr.split(",")[0].trim());
        bufferRight = Long.parseLong(buffStr.split(",")[1].trim());
      } else {
        bufferLeft = bufferRight = Long.parseLong(buffStr.trim());
      }
      
    } catch (NumberFormatException e) {
      JOptionPane.showMessageDialog(null, "You didn't enter a valid number for the buffer option!", "CSGF Error", JOptionPane.ERROR_MESSAGE);
      System.exit(0);
      return;
    } catch(NullPointerException e) {
      System.exit(0);
      return;
    } catch(ArrayIndexOutOfBoundsException e) {
      JOptionPane.showMessageDialog(null, "You didn't enter a valid number for the buffer option!", "CSGF Error", JOptionPane.ERROR_MESSAGE);
      System.exit(0);
      return;
    }
    URL url3 = null;
    Scanner sc2 = null;
    Scanner sc3 = null;
    String assembly = "";
    String txid = "";
    String niceTitle = "";
    ArrayList<String> errorList = new ArrayList<String>();
    try {
      URL url2 = new URL("http://www.ncbi.nlm.nih.gov/genome/?term="+URLEncoder.encode(species, "UTF-8"));
      sc2 = new Scanner(url2.openStream());
      while(sc2.hasNextLine()) {
        String line = sc2.nextLine().trim();
        if(line.startsWith("<title>") && line.endsWith("</title>")) {
          niceTitle =  line.split(">")[1].split("-")[0].trim();
          String id = niceTitle.split("\\(ID ")[1].split("\\)")[0];
          o.println("Loaded basic info for " + niceTitle);
          url3 = new URL("http://www.ncbi.nlm.nih.gov/assembly?LinkName=genome_assembly&from_uid="+URLEncoder.encode(id, "UTF-8"));
          break;
        }
      }
      
      sc3 = new Scanner(url3.openStream());
      while(sc3.hasNextLine()) {
        String line = sc3.nextLine().trim();
        
        if(line.contains("GenBank assembly accession: </dt><dd>")) {
          assembly = line.split("GenBank assembly accession: </dt><dd>")[1].split(" ")[0].trim();
        }
        if(line.contains("href=\"/genome/?term=txid")) {
          txid = line.split("href=\"/genome/\\?term=txid")[1].split("\\[")[0].trim();
        }
      }
    } catch (MalformedURLException e) {
      o.println("ERROR: Species search makes invalid URL!");
      
      return;
    } catch (UnsupportedEncodingException e) {
      o.println("ERROR: Failed to URL-encode species name!");
      
      return;
    } catch (IOException e) {
      e.printStackTrace();
      o.println("ERROR: Failed to load species info!");
      
      return;
    } catch (ArrayIndexOutOfBoundsException e) {
      o.println("ERROR: Species not found in NCBI database! (or they changed their HTML)");
      
      return;
    } catch (NullPointerException e) {
      o.println("ERROR: NPE (you shouldn't see this)");
      return;
    } finally {
      if(sc2 != null) sc2.close();
      if(sc3 != null) sc3.close();
    }
    String dbname1 = "genomic/" + txid + "/" + assembly;
    o.println("Got database name: " + dbname1);
    
    for(String geneQuery : queryList) {
      PrintWriter wInfo = null;
      try {
        new File("Results/"+species+"/"+geneQuery).mkdirs(); 
        wInfo = new PrintWriter("Results/"+species+"/"+geneQuery+"/Info.txt", "UTF-8");
      } catch (UnsupportedEncodingException e) {
        o.println("ERROR: Failed to create results file!");
        continue;
      }
      catch(IOException e) {
        o.println("ERROR: Failed to create or write to results file or folder!");
        
        continue;
      }catch (NullPointerException e) {
        o.println("ERROR: NPE (you shouldn't see this)");
        continue;
      }
      
      wInfo.println("Searching in Species Genome: " + niceTitle);
      wInfo.println("Maximum evalue: " + maxEvalue);
      
      String aminoSeq = null;
      String geneName = null;
      Scanner sc1 = null;
      try {
        URL url1 = new URL("http://www.ncbi.nlm.nih.gov/sviewer/viewer.cgi?tool=portal&sendto=on&log$=seqview&db=protein&dopt=fasta&sort=&val="+URLEncoder.encode(geneQuery, "UTF-8")+"&from=begin&to=end&maxplex=1");
        sc1 = new Scanner(url1.openStream());
        sc1.useDelimiter("\0");
        aminoSeq = sc1.next().trim();
        geneName = aminoSeq.split("\n")[0].split("\\|")[4].trim();
        o.println("Gene name: "+ geneName);
        wInfo.println("NCBI Gene Query Used: " + geneQuery + " - " + geneName);
      } catch (MalformedURLException e) {
        o.println("ERROR: Gene query makes invalid URL!");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: INVALID GENE QUERY! ***");
        wInfo.close();
        
        continue;
      } catch (NullPointerException e) {
        o.println("ERROR: NPE (you shouldn't see this)");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: NPE (you shouldn't see this) ***");
        wInfo.close();
        continue;
      } catch (UnsupportedEncodingException e) {
        o.println("ERROR: Failed to URL-encode gene query!");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: INVALID GENE QUERY! ***");
        wInfo.close();
        
        continue;
      } catch (IOException e) {
        o.println("ERROR: Failed to load gene query info!");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: FAILED TO LOAD GENE QUERY INFO! ***");
        wInfo.close();
        
        continue;
      } catch (ArrayIndexOutOfBoundsException e) {
        o.println("ERROR: Gene query not found in NCBI database! (or they changed their HTML)");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: GENE QUERY NOT FOUND! ***");
        wInfo.close();
        
        continue;
      } finally {
        if(sc1 != null) sc1.close();
      }
      String tblastnRID = "";
      try {
        o.print("Starting TBLASTN... ");
        String tblastnQuery = "CMD=Put&QUERY="+URLEncoder.encode(aminoSeq, "UTF-8")+"&BLAST_SPEC=Assembly&PROGRAM=tblastn&SERVICE=plain&DATABASE="+URLEncoder.encode(dbname1, "UTF-8");
        tblastnRID = doBlastAndGetRID(tblastnQuery,wInfo);
      } catch (UnsupportedEncodingException e) {
        o.println("ERROR: Failed to URL-encode TBLASTN query!");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: INVALID TBLASTN URL! ***");
        wInfo.close();
        
        continue;
      } catch (ArrayIndexOutOfBoundsException e) {
        o.println("ERROR: Invalid TBLASTN query (or they changed their HTML)");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: INVALID TBLASTN QUERY! ***");
        wInfo.close();
        
        continue;
      }
      catch(IOException e) {
        o.println("ERROR: Failed to upload TBLASTN query!");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: FAILED TO UPLOAD TBLASTN QUERY! ***");
        wInfo.close();
        
        continue;
        
      } catch (NullPointerException e) {
        o.println("ERROR: NPE (you shouldn't see this)");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: NPE (you shouldn't see this) ***");
        
        continue;
      }
      if(tblastnRID.equals("")) {
        o.println("ERROR: Invalid TBLASTN result (or they changed their HTML)");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: INVALID TBLASTN RESULT! ***");
        wInfo.close();
        
        continue;
      }
      
      Map<String,String> mapIDToDescription = new TreeMap<String,String>();
      Map<String,Long> mapLowestForEachID = new TreeMap<String,Long>();
      Map<String,Long> mapHighestForEachID = new TreeMap<String,Long>();
      Map<String,Double> mapEvalueForEachID = new TreeMap<String,Double>();
      Map<String,Long> mapLengthForEachID = new TreeMap<String,Long>();
      Scanner sc6 = null;
      try {
        o.print("Searching for results in genome of " + species + " (TXID " + txid + ")...");
        URL url6 = new URL("http://blast.ncbi.nlm.nih.gov/Blast.cgi?RESULTS_FILE=on&RID="+tblastnRID+"&FORMAT_TYPE=Text&FORMAT_OBJECT=Alignment&ALIGNMENT_VIEW=Tabular&CMD=Get");
        sc6 = new Scanner(url6.openStream());
        
        while(sc6.hasNextLine()) {
          String line = sc6.nextLine().trim();
          if(line.startsWith("#")) continue;
          if(line.equals("")) continue;
          boolean isRightSpecies = true;
          // # Fields: query id, subject ids, % identity, % positives, query/sbjct frames, alignment length, mismatches, gap opens, q. start, q. end, s. start, s. end, evalue, bit score
          String someKindaID = line.split("\t")[1].split("\\|")[1];
          String description = mapIDToDescription.get(someKindaID);
          if(description == null) {
            // Find description of organism, because not in text file
            URL url7 = new URL("http://www.ncbi.nlm.nih.gov/nucleotide/" + someKindaID);
            Scanner sc7 = new Scanner(url7.openStream());
            while(sc7.hasNextLine()) {
              String line2 = sc7.nextLine().trim();
              if(line2.contains("?ORGANISM=")) {
                if(line2.contains("?ORGANISM="+txid+"&")) {
                  isRightSpecies = true;
                } else {
                  isRightSpecies = false;
                  mapIDToDescription.put(someKindaID,"");
                  break;
                }
              }
              if(line2.startsWith("<h1>") && line2.endsWith("</h1>")) {
                description = line2.split("<h1>")[1].split("</h1>")[0];
                mapIDToDescription.put(someKindaID,description);
                mapLowestForEachID.put(someKindaID,Long.MAX_VALUE);
                mapHighestForEachID.put(someKindaID,(long)-1);
              }
              if(line2.contains("SequenceSize=\"")) {
                long size = Long.parseLong(line2.split("SequenceSize=\"")[1].split("\"")[0]);
                mapLengthForEachID.put(someKindaID,size);
              }
            }
            sc7.close();
          }
          if(description.equals("")) isRightSpecies = false;
          if(isRightSpecies){
            long subjectStart = Long.parseLong(line.split("\t")[10]);
            long subjectEnd = Long.parseLong(line.split("\t")[11]);
            double evalue = Double.parseDouble(line.split("\t")[12]);
            
            if(evalue <= maxEvalue) {
              if((mapEvalueForEachID.containsKey(someKindaID) && mapEvalueForEachID.get(someKindaID) < evalue) || (!mapEvalueForEachID.containsKey(someKindaID))) {
                mapEvalueForEachID.put(someKindaID,evalue);
              }
              if(subjectStart < mapLowestForEachID.get(someKindaID)) mapLowestForEachID.put(someKindaID,subjectStart);
              if(subjectEnd > mapHighestForEachID.get(someKindaID)) mapHighestForEachID.put(someKindaID,subjectEnd);
            }
          }
        }
        sc6.close();
        o.println(" Finished!");
      } catch (MalformedURLException e) {
        o.println("ERROR: Gene search makes invalid URL!");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: INVALID GENE NAME! ***");
        
        
        continue;
      } catch (UnsupportedEncodingException e) {
        o.println("ERROR: Failed to URL-encode gene name!");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: INVALID GENE NAME! ***");
        
        
        continue;
      } catch (IOException e) {
        o.println("ERROR: Failed to load gene info!");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: FAILED TO LOAD GENE INFO! ***");
        
        
        continue;
      } catch (ArrayIndexOutOfBoundsException e) {
        o.println("ERROR: Gene description not found in NCBI database! (or they changed their HTML)");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: GENE NOT FOUND! ***");
        
        
        continue;
      } catch (NullPointerException e) {
        o.println("ERROR: NPE (you shouldn't see this)");
        if(!errorList.contains(geneQuery)) {
          errorList.add(geneQuery);
        }
        wInfo.println("*** ERROR: NPE (you shouldn't see this) ***");
        
        continue;
      } finally {
        if(sc6 != null) sc6.close();
      }
      wInfo.println("");
      mapEvalueForEachID = sortByValue(mapEvalueForEachID);
      int doneGenes = 1;
      for (String currID : mapEvalueForEachID.keySet()) {
        if(doneGenes > 5) break;
        doneGenes++;
        String currDesc = mapIDToDescription.get(currID);
        double evalue = mapEvalueForEachID.get(currID);
        long currStart = mapLowestForEachID.get(currID);
        long currEnd = mapHighestForEachID.get(currID);
        long currLen = mapLengthForEachID.get(currID);
        wInfo.println("TBLASTN Match Name: " + currDesc);
        wInfo.println("NCBI ID: " + currID);
        wInfo.println("True Match Range (unpadded): " + currStart + "-" + currEnd);
        // Add buffer on either side, if possible
        if((currStart - bufferLeft) < 1) {
          currStart = 1;
        } else {
          currStart -= bufferLeft;
        }
        if((currEnd + bufferRight) > currLen) {
          currEnd = currLen;
        } else {
          currEnd += bufferRight;
        }
        o.println("Downloading FASTA for ID " + currID + ": " + currDesc +" {" + currStart + "-" + currEnd+"}, evalue " + evalue);
        wInfo.println("FASTA File Range (padded by "+buffStr+" bases): " + currStart + "-" + currEnd);
        wInfo.println("Match evalue: " + evalue);
        String strFASTA = null;
        try {
          URL urlFASTA = new URL("http://www.ncbi.nlm.nih.gov/projects/sviewer/sequence.cgi?id="+currID+"&format=fasta&ranges=" + (currStart - 1) + "-" + (currEnd - 1));
          Scanner sc8 = new Scanner(urlFASTA.openStream());
          sc8.useDelimiter("\0");
          strFASTA = sc8.next().trim();
          sc8.close();
        } catch (MalformedURLException e) {
          o.println("ERROR: FASTA download makes invalid URL!");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: INVALID FASTA URL! ***");
          
          
          continue;
        }  catch (NullPointerException e) {
          o.println("ERROR: NPE (you shouldn't see this)");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: NPE (you shouldn't see this) ***");
          
          continue;
        }
        catch (IOException e) {
          o.println("ERROR: Failed to download FASTA!");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: FAILED TO DOWNLOAD FASTA! ***");
          
          
          continue;
        }
        if(strFASTA == null || strFASTA.equals("")) {
          o.println("ERROR: Failed to download FASTA!");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: FAILED TO DOWNLOAD FASTA! ***");
          
          
          continue;
        }
        try {
          saveTextFile(strFASTA,"Results/"+species+"/"+geneQuery+"/FASTA_ID" + currID + "_" + currStart + "-" + currEnd + ".fa");
        } catch (IOException e) {
          o.println("ERROR: Failed to save FASTA file!");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: FAILED TO SAVE FASTA FILE! ***");
          
          
          continue;
        }
        o.print("Starting BLASTX to verify results... ");
        String blastxRID = "";
        try {
          String blastxQuery = "CMD=Put&QUERY="+URLEncoder.encode(strFASTA, "UTF-8")+"&PROGRAM=blastx&SERVICE=plain&DATABASE=nr";
          blastxRID = doBlastAndGetRID(blastxQuery,wInfo);
          wInfo.println("BLASTX Results Link: http://blast.ncbi.nlm.nih.gov/Blast.cgi?CMD=Get&RID="+blastxRID);
        } catch (UnsupportedEncodingException e) {
          o.println("ERROR: Failed to URL-encode BLASTX query!");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: INVALID BLASTX URL! ***");
          
          continue;
        } catch (ArrayIndexOutOfBoundsException e) {
          o.println("ERROR: Invalid BLASTX query (or they changed their HTML)");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: INVALID BLASTX QUERY! ***");
          
          
          continue;
        }
        catch(IOException e) {
          o.println("ERROR: Failed to upload BLASTX query!");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: FAILED TO UPLOAD BLASTX QUERY! ***");
          
          continue;
        }
        catch (NullPointerException e) {
          o.println("ERROR: NPE (you shouldn't see this)");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: NPE (you shouldn't see this) ***");
          
          continue;
        }
        if(blastxRID.equals("")) {
          o.println("ERROR: Invalid BLASTX result (or they changed their HTML)");
          if(!errorList.contains(geneQuery)) {
            errorList.add(geneQuery);
          }
          wInfo.println("*** ERROR: INVALID BLASTX RESULT! ***");
          
          continue;
        }
        wInfo.println("");
        
      }
      wInfo.close();
      o.println("");
    }
    if(batch) {
      try {
        String l = System.lineSeparator();
        String dt = new Date().toString().replace(":","-");
        String joinedStr = "";
        for(String s : errorList) {
          joinedStr+=s+l;
        }
        saveTextFile("Total Number of Errors: " + errorList.size() + l + "Date: " + dt+l+l+joinedStr, "Results/"+species+"/ErrorList_"+dt+".txt");
      } catch (IOException e) {
        o.println("ERROR: Failed to save error list file!");
        
      }
    }
    if(errorList.size() > 0) {
      o.println("*** Total Number of Errors: " + errorList.size());
      if(batch)
        o.print(" *** Errors in Genes: ");
      for(String s : errorList) {
        o.print(s+" ");
      }
      o.println();
      
      playWav("resources/error.wav");
    } else {
      playWav("resources/tada.wav");
    }
    o.println("All done, yay!");
    int option2 = JOptionPane.showConfirmDialog(null, "Do you want to open the results folder?", "CSGF", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
    if (option2 == JOptionPane.YES_OPTION) {
      try {
        Desktop.getDesktop().open(new File("Results/"));
      } catch(Exception e) {
        JOptionPane.showMessageDialog(null, "Error opening results folder!\n\n"+e.getMessage(), "CSGF Error", JOptionPane.ERROR_MESSAGE);
      }
    }
    System.exit(0);
  }
  
// HTTP POST request
  public static InputStream doPost(String url, String query) throws MalformedURLException{
    try {
      URL obj = new URL(url);
      HttpURLConnection con = (HttpURLConnection) obj.openConnection();
      
      //add request header
      con.setRequestMethod("POST");
      
      // Send post request
      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.writeBytes(query);
      wr.flush();
      wr.close();
      
      return con.getInputStream();
    } catch(IOException e) {
      return null;
    }
  }
  
  public static String doBlastAndGetRID(String query,PrintWriter wInfo) throws IOException, MalformedURLException, ArrayIndexOutOfBoundsException, NullPointerException {
    boolean blastx = query.contains("&PROGRAM=blastx&");
    String blastRID = "";
    int i = 0;
    do { // no retry if blastx
      InputStream postStream = doPost("http://www.ncbi.nlm.nih.gov/blast/Blast.cgi",query);
      Scanner sc4 = new Scanner(postStream);
      blastRID = "";
      while(sc4.hasNextLine()) {
        String line = sc4.nextLine().trim();
        if(line.startsWith("RID = ")) {
          blastRID = line.split("=")[1].trim();
        }
      }
      sc4.close();
      o.println("Query RID is " + blastRID);
      
      String blastStatus = "WAITING";
      URL url5 = new URL("http://blast.ncbi.nlm.nih.gov/Blast.cgi?CMD=Get&NOHEADER=true&RID="+blastRID);
      o.print("Waiting for results");
      int totalSlept = 0;
      while(blastStatus.equals("WAITING")) {
        Scanner sc5 = new Scanner(url5.openStream());
        while(sc5.hasNextLine()) {
          String line = sc5.nextLine().trim();
          if(line.startsWith("Status=")) {
            blastStatus = line.split("=")[1].trim();
            if(blastStatus.equals("READY")) {
              o.println(" Finished!");
              sc5.close();
              o.println("Waiting 30 seconds to be nice to NCBI's server...");
              mySleep(30000);
              return blastRID;
            }
            if(blastStatus.equals("FAILED")) {
              o.println(" BLAST Server Said Failed!");
              wInfo.println("*** BLAST SERVER SAID FAILED! ***");
              sc5.close();
              o.println("Waiting 30 seconds to be nice to NCBI's server...");
              mySleep(30000);
              return "";
            }
          }
          else if(line.startsWith("var tm = \"") && !line.contains("\"\"")) {
            int time = Integer.parseInt(line.split("\"")[1].trim());
            o.print(".");
            mySleep(time); // in ms
            totalSlept += time;
            if((!blastx && totalSlept > 300000) || (blastx && totalSlept > 900000)) { // 5 min or 15 if blastx
              o.println(" ERROR!\nBLAST server seems to have hung. Canceling query, waiting 30 sec and retrying. (Try "+(i+1)+"/5)");
              URL urlCancel = new URL("http://blast.ncbi.nlm.nih.gov/Blast.cgi?CMD=Cancel&RID="+blastRID);
              Scanner sc5a = new Scanner(urlCancel.openStream());
              sc5a.nextLine(); // just read something
              sc5a.close();
              mySleep(30000);
              blastStatus = ""; // cheat
              totalSlept = 0;
              break;
            }
          }
        }
        sc5.close();
      }
      i++;
    } while(i < 5 && !blastx);
    return "";
  }
  public static void saveTextFile(String contents, String path) throws IOException {
    PrintWriter out = new PrintWriter(new FileWriter(new File(path)));
    out.println(contents);
    out.close();
  }
  public static void playWav(String path) {
    try {
      File yourFile = new File(path);
      AudioInputStream stream;
      AudioFormat format;
      DataLine.Info info;
      Clip clip;
      
      stream = AudioSystem.getAudioInputStream(yourFile);
      format = stream.getFormat();
      info = new DataLine.Info(Clip.class, format);
      clip = (Clip) AudioSystem.getLine(info);
      clip.open(stream);
      clip.start();
      while (!clip.isRunning())
        Thread.sleep(10);
      while (clip.isRunning())
        Thread.sleep(10);
      clip.close();
    }
    catch (Exception e) {
      o.println("WARNING: couldn't find or play sound: " + path);
    }
  }
  public static void mySleep(long time) {
    try {
      Thread.sleep(time);
    } catch(InterruptedException e) {
      // Restore the interrupted status
      Thread.currentThread().interrupt();
    }
  }
  private static class ValueComparator<K , V extends Comparable<V>> implements Comparator<K>
  {
    Map<K, V> map;
    
    public ValueComparator(Map<K, V> map) {
      this.map = map;
    }
    
    @Override
    public int compare(K keyA, K keyB) {
      Comparable<V> valueA = map.get(keyA);
      V valueB = map.get(keyB);
      return valueA.compareTo(valueB);
    }
    
  }
  
  public static<K, V extends Comparable<V>> Map<K, V> sortByValue(Map<K, V> unsortedMap)
  {
    Map<K, V> sortedMap = new
      TreeMap<K, V>(new ValueComparator<K, V>(unsortedMap));
    sortedMap.putAll(unsortedMap);
    return sortedMap;
  }
}