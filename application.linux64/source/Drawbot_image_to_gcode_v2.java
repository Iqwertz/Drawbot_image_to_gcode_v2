import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import java.util.Map; 
import processing.pdf.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class Drawbot_image_to_gcode_v2 extends PApplet {

///////////////////////////////////////////////////////////////////////////////////////////////////////
// My Drawbot, "Death to Sharpie"
// Jpeg to gcode simplified (kinda sorta works version, v3.75 (beta))
//
// Scott Cooper, Dullbits.com, <scottslongemailaddress@gmail.com>
//
// Open creative GPL source commons with some BSD public GNU foundation stuff sprinkled in...
// If anything here is remotely useable, please give me a shout.
//
// Useful math:    http://members.chello.at/~easyfilter/bresenham.html
// GClip:          https://forum.processing.org/two/discussion/6179/why-does-not-it-run-clipboard
// Dynamic class:  https://processing.org/discourse/beta/num_1262759715.html
///////////////////////////////////////////////////////////////////////////////////////////////////////




// Constants 
final float   paper_size_x = 32 * 25.4f;
final float   paper_size_y = 40 * 25.4f;
final float   image_size_x = 28 * 25.4f;
final float   image_size_y = 36 * 25.4f;
final float   paper_top_to_origin = 285;      //mm, make smaller to move drawing down on paper
final float   pen_width = 0.65f;               //mm, determines image_scale, reduce, if solid black areas are speckled with white holes.
final int     pen_count = 6;
final char    gcode_decimal_seperator = '.';    
final int     gcode_decimals = 2;             // Number of digits right of the decimal point in the gcode files.
final int     svg_decimals = 2;               // Number of digits right of the decimal point in the SVG file.
final float   grid_scale = 25.4f;              // Use 10.0 for centimeters, 25.4 for inches, and between 444 and 529.2 for cubits.


// Every good program should have a shit pile of badly named globals.
Class cl = null;
pfm ocl;
int current_pfm = 0;
String[] pfms = {"PFM_original", "PFM_spiral", "PFM_squares"}; 

int     state = 1;
int     pen_selected = 0;
int     current_copic_set = 0;
int     display_line_count;
String  display_mode = "drawing";
PImage  img_orginal;               // The original image
PImage  img_reference;             // After pre_processing, croped, scaled, boarder, etc.  This is what we will try to draw. 
PImage  img;                       // Used during drawing for current brightness levels.  Gets damaged during drawing.
float   gcode_offset_x;
float   gcode_offset_y;
float   gcode_scale;
float   screen_scale;
float   screen_scale_org;
int     screen_rotate = 0;
float   old_x = 0;
float   old_y = 0;
int     mx = 0;
int     my = 0;
int     morgx = 0;
int     morgy = 0;
int     pen_color = 0;
boolean is_pen_down;
boolean is_grid_on = false;
String  path_selected = "";
String  file_selected = "";
String  basefile_selected = "";
String  gcode_comments = "";
int     startTime = 0;
boolean ctrl_down = false;

Limit   dx, dy;
Copix   copic;
PrintWriter OUTPUT;
botDrawing d1;

float[] pen_distribution = new float[pen_count];

String[][] copic_sets = {
  {"100", "N10", "N8", "N6", "N4", "N2"},       // Dark Greys
  {"100", "100", "N7", "N5", "N3", "N2"},       // Light Greys
  {"100", "W10", "W8", "W6", "W4", "W2"},       // Warm Greys
  {"100", "C10", "C8", "C6", "C4", "C2"},       // Cool Greys
  {"100", "100", "C7", "W5", "C3", "W2"},       // Mixed Greys
  {"100", "100", "W7", "C5", "W3", "C2"},       // Mixed Greys
  {"100", "100", "E49", "E27", "E13", "E00"},   // Browns
  {"100", "100", "E49", "E27", "E13", "N2"},    // Dark Grey Browns
  {"100", "100", "E49", "E27", "N4", "N2"},     // Browns
  {"100", "100", "E49", "N6", "N4", "N2"},      // Dark Grey Browns
  {"100", "100", "B37", "N6", "N4", "N2"},      // Dark Grey Blues
  {"100", "100", "R59", "N6", "N4", "N2"},      // Dark Grey Red
  {"100", "100", "G29", "N6", "N4", "N2"},      // Dark Grey Violet
  {"100", "100", "YR09", "N6", "N4", "N2"},     // Dark Grey Orange
  {"100", "100", "B39", "G28", "B26", "G14"},   // Blue Green
  {"100", "100", "B39", "V09", "B02", "V04"},   // Purples
  {"100", "100", "R29", "R27", "R24", "R20"},   // Reds
  {"100", "E29", "YG99", "Y17", "YG03", "Y11"} // Yellow, green
};



///////////////////////////////////////////////////////////////////////////////////////////////////////
public void setup() {
  
  frame.setLocation(200, 200);
  surface.setResizable(true);
  surface.setTitle("Drawbot_image_to_gcode_v2, version 3.75");
  colorMode(RGB);
  frameRate(999);
  //randomSeed(millis());
  randomSeed(3);
  d1 = new botDrawing();
  dx = new Limit(); 
  dy = new Limit(); 
  copic = new Copix();
  loadInClass(pfms[current_pfm]);

  // If the clipboard contains a URL, try to download the picture instead of using local storage.
  String url = GClip.paste();
  if (match(url.toLowerCase(), "^https?:...*(jpg|png)") != null) {
    println("Image URL found on clipboard: "+ url);
    path_selected = url;
    state++;
  } else {
    println("image URL not found on clipboard");
    selectInput("Select an image to process:", "fileSelected");
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void draw() {
  if (state != 3) { background(255, 255, 255); }
  scale(screen_scale);
  translate(mx, my);
  rotate(HALF_PI*screen_rotate);
  
  switch(state) {
  case 1: 
    //println("State=1, Waiting for filename selection");
    break;
  case 2:
    //println("State=2, Setup squiggles");
    loop();
    setup_squiggles();
    startTime = millis();
    break;
  case 3: 
    //println("State=3, Drawing image");
    if (display_line_count <= 1) {
      background(255);
    } 
    ocl.find_path();
    display_line_count = d1.line_count;
    break;
  case 4: 
    println("State=4, pfm.post_processing");
    ocl.post_processing();

    set_even_distribution();
    normalize_distribution();
    d1.evenly_distribute_pen_changes(d1.get_line_count(), pen_count);
    d1.distribute_pen_changes_according_to_percentages(display_line_count, pen_count);

    println("elapsed time: " + (millis() - startTime) / 1000.0f + " seconds");
    display_line_count = d1.line_count;
  
    gcode_comment ("extreams of X: " + dx.min + " thru " + dx.max);
    gcode_comment ("extreams of Y: " + dy.min + " thru " + dy.max);
    state++;
    break;
  case 5: 
    render_all();
    noLoop();
    break;
  default:
    println("invalid state: " + state);
    break;
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void fileSelected(File selection) {
  if (selection == null) {
    println("no image file selected, exiting program.");
    exit();
  } else {
    path_selected = selection.getAbsolutePath();
    file_selected = selection.getName();
    String[] fileparts = split(file_selected, '.');
    basefile_selected = fileparts[0];
    println("user selected: " + path_selected);
    //println("user selected: " + file_selected);
    //println("user selected: " + basefile_selected);
    state++;
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void setup_squiggles() {
  float   gcode_scale_x;
  float   gcode_scale_y;
  float   screen_scale_x;
  float   screen_scale_y;

  //println("setup_squiggles...");

  d1.line_count = 0;
  //randomSeed(millis());
  img = loadImage(path_selected, "jpeg");  // Load the image into the program  
  gcode_comment("loaded image: " + path_selected);

  image_rotate();

  img_orginal = createImage(img.width, img.height, RGB);
  img_orginal.copy(img, 0, 0, img.width, img.height, 0, 0, img.width, img.height);

  ocl.pre_processing();
  img.loadPixels();
  img_reference = createImage(img.width, img.height, RGB);
  img_reference.copy(img, 0, 0, img.width, img.height, 0, 0, img.width, img.height);
  
  gcode_scale_x = image_size_x / img.width;
  gcode_scale_y = image_size_y / img.height;
  gcode_scale = min(gcode_scale_x, gcode_scale_y);
  gcode_offset_x = - (img.width * gcode_scale / 2.0f);  
  gcode_offset_y = - (paper_top_to_origin - (paper_size_y - (img.height * gcode_scale)) / 2.0f);

  screen_scale_x = width / (float)img.width;
  screen_scale_y = height / (float)img.height;
  screen_scale = min(screen_scale_x, screen_scale_y);
  screen_scale_org = screen_scale;
  
  gcode_comment("final dimensions: " + img.width + " by " + img.height);
  gcode_comment("paper_size: " + nf(paper_size_x,0,2) + " by " + nf(paper_size_y,0,2) + "      " + nf(paper_size_x/25.4f,0,2) + " by " + nf(paper_size_y/25.4f,0,2));
  gcode_comment("drawing size max: " + nf(image_size_x,0,2) + " by " + nf(image_size_y,0,2) + "      " + nf(image_size_x/25.4f,0,2) + " by " + nf(image_size_y/25.4f,0,2));
  gcode_comment("drawing size calculated " + nf(img.width * gcode_scale,0,2) + " by " + nf(img.height * gcode_scale,0,2) + "      " + nf(img.width * gcode_scale/25.4f,0,2) + " by " + nf(img.height * gcode_scale/25.4f,0,2));
  gcode_comment("gcode_scale X:  " + nf(gcode_scale_x,0,2));
  gcode_comment("gcode_scale Y:  " + nf(gcode_scale_y,0,2));
  gcode_comment("gcode_scale:    " + nf(gcode_scale,0,2));
  //gcode_comment("screen_scale X: " + nf(screen_scale_x,0,2));
  //gcode_comment("screen_scale Y: " + nf(screen_scale_y,0,2));
  //gcode_comment("screen_scale:   " + nf(screen_scale,0,2));
  ocl.output_parameters();

  state++;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void render_all() {
  println("render_all: " + display_mode + ", " + display_line_count + " lines, with pen set " + current_copic_set);
  
  if (display_mode == "drawing") {
    //<d1.render_all();
    d1.render_some(display_line_count);
  }

  if (display_mode == "pen") {
    //image(img, 0, 0);
    d1.render_one_pen(display_line_count, pen_selected);
  }
  
  if (display_mode == "original") {
    image(img_orginal, 0, 0);
  }

  if (display_mode == "reference") {
    image(img_reference, 0, 0);
  }
  
  if (display_mode == "lightened") {
    image(img, 0, 0);
  }
  grid();
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void keyReleased() {
  if (keyCode == CONTROL) { ctrl_down = false; }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void keyPressed() {
  if (keyCode == CONTROL) { ctrl_down = true; }

  if (key == 'p') {
    current_pfm ++;
    if (current_pfm >= pfms.length) { current_pfm = 0; }
    //display_line_count = 0;
    loadInClass(pfms[current_pfm]); 
    state = 2;
  }
  
  if (key == 'd') { display_mode = "drawing";   }
  if (key == 'O') { display_mode = "original";  }
  if (key == 'o') { display_mode = "reference";  }
  if (key == 'l') { display_mode = "lightened"; }
  if (keyCode == 49 && ctrl_down && pen_count > 0) { display_mode = "pen";  pen_selected = 0; }  // ctrl 1
  if (keyCode == 50 && ctrl_down && pen_count > 1) { display_mode = "pen";  pen_selected = 1; }  // ctrl 2
  if (keyCode == 51 && ctrl_down && pen_count > 2) { display_mode = "pen";  pen_selected = 2; }  // ctrl 3
  if (keyCode == 52 && ctrl_down && pen_count > 3) { display_mode = "pen";  pen_selected = 3; }  // ctrl 4
  if (keyCode == 53 && ctrl_down && pen_count > 4) { display_mode = "pen";  pen_selected = 4; }  // ctrl 5
  if (keyCode == 54 && ctrl_down && pen_count > 5) { display_mode = "pen";  pen_selected = 5; }  // ctrl 6
  if (keyCode == 55 && ctrl_down && pen_count > 6) { display_mode = "pen";  pen_selected = 6; }  // ctrl 7
  if (keyCode == 56 && ctrl_down && pen_count > 7) { display_mode = "pen";  pen_selected = 7; }  // ctrl 8
  if (keyCode == 57 && ctrl_down && pen_count > 8) { display_mode = "pen";  pen_selected = 8; }  // ctrl 9
  if (keyCode == 48 && ctrl_down && pen_count > 9) { display_mode = "pen";  pen_selected = 9; }  // ctrl 0
  if (key == 'G') { is_grid_on = ! is_grid_on; }
  if (key == ']') { screen_scale *= 1.05f; }
  if (key == '[') { screen_scale *= 1 / 1.05f; }
  if (key == '1' && pen_count > 0) { pen_distribution[0] *= 1.1f; }
  if (key == '2' && pen_count > 1) { pen_distribution[1] *= 1.1f; }
  if (key == '3' && pen_count > 2) { pen_distribution[2] *= 1.1f; }
  if (key == '4' && pen_count > 3) { pen_distribution[3] *= 1.1f; }
  if (key == '5' && pen_count > 4) { pen_distribution[4] *= 1.1f; }
  if (key == '6' && pen_count > 5) { pen_distribution[5] *= 1.1f; }
  if (key == '7' && pen_count > 6) { pen_distribution[6] *= 1.1f; }
  if (key == '8' && pen_count > 7) { pen_distribution[7] *= 1.1f; }
  if (key == '9' && pen_count > 8) { pen_distribution[8] *= 1.1f; }
  if (key == '0' && pen_count > 9) { pen_distribution[9] *= 1.1f; }
  if (key == '!' && pen_count > 0) { pen_distribution[0] *= 0.9f; }
  if (key == '@' && pen_count > 1) { pen_distribution[1] *= 0.9f; }
  if (key == '#' && pen_count > 2) { pen_distribution[2] *= 0.9f; }
  if (key == '$' && pen_count > 3) { pen_distribution[3] *= 0.9f; }
  if (key == '%' && pen_count > 4) { pen_distribution[4] *= 0.9f; }
  if (key == '^' && pen_count > 5) { pen_distribution[5] *= 0.9f; }
  if (key == '&' && pen_count > 6) { pen_distribution[6] *= 0.9f; }
  if (key == '*' && pen_count > 7) { pen_distribution[7] *= 0.9f; }
  if (key == '(' && pen_count > 8) { pen_distribution[8] *= 0.9f; }
  if (key == ')' && pen_count > 9) { pen_distribution[9] *= 0.9f; }
  if (key == 't') { set_even_distribution(); }
  if (key == 'y') { set_black_distribution(); }
  if (key == 'x') { mouse_point(); }  
  if (key == '}' && current_copic_set < copic_sets.length -1) { current_copic_set++; }
  if (key == '{' && current_copic_set >= 1)                   { current_copic_set--; }
  
  if (key == 's') { if (state == 3) { state++; } }
  if (keyCode == 65 && ctrl_down)  {
    println("Holly freak, Ctrl-A was pressed!");
  }
  if (key == '9') {
    if (pen_count > 0) { pen_distribution[0] *= 1.00f; }
    if (pen_count > 1) { pen_distribution[1] *= 1.05f; }
    if (pen_count > 2) { pen_distribution[2] *= 1.10f; }
    if (pen_count > 3) { pen_distribution[3] *= 1.15f; }
    if (pen_count > 4) { pen_distribution[4] *= 1.20f; }
    if (pen_count > 5) { pen_distribution[5] *= 1.25f; }
    if (pen_count > 6) { pen_distribution[6] *= 1.30f; }
    if (pen_count > 7) { pen_distribution[7] *= 1.35f; }
    if (pen_count > 8) { pen_distribution[8] *= 1.40f; }
    if (pen_count > 9) { pen_distribution[9] *= 1.45f; }
  }
  if (key == '0') {
    if (pen_count > 0) { pen_distribution[0] *= 1.00f; }
    if (pen_count > 1) { pen_distribution[1] *= 0.95f; }
    if (pen_count > 2) { pen_distribution[2] *= 0.90f; }
    if (pen_count > 3) { pen_distribution[3] *= 0.85f; }
    if (pen_count > 4) { pen_distribution[4] *= 0.80f; }
    if (pen_count > 5) { pen_distribution[5] *= 0.75f; }
    if (pen_count > 6) { pen_distribution[6] *= 0.70f; }
    if (pen_count > 7) { pen_distribution[7] *= 0.65f; }
    if (pen_count > 8) { pen_distribution[8] *= 0.60f; }
    if (pen_count > 9) { pen_distribution[9] *= 0.55f; }
}
  if (key == 'g') { 
    create_gcode_files(display_line_count);
    create_gcode_test_file ();
    create_svg_file(display_line_count);
    d1.render_to_pdf(display_line_count);
    d1.render_each_pen_to_pdf(display_line_count);
  }

  if (key == '\\') { screen_scale = screen_scale_org; screen_rotate=0; mx=0; my=0; }
  if (key == '<') {
    int delta = -10000;
    display_line_count = PApplet.parseInt(display_line_count + delta);
    display_line_count = constrain(display_line_count, 0, d1.line_count);
    //println("display_line_count: " + display_line_count);
  }
  if (key == '>') {
    int delta = 10000;
    display_line_count = PApplet.parseInt(display_line_count + delta);
    display_line_count = constrain(display_line_count, 0, d1.line_count);
    //println("display_line_count: " + display_line_count);
  }
  if (key == CODED) {
    int delta = 15;
    if (keyCode == UP)    { my+= delta; };
    if (keyCode == DOWN)  { my-= delta; };
    if (keyCode == RIGHT) { mx-= delta; };
    if (keyCode == LEFT)  { mx+= delta; };
  }
  if (key == 'r') { 
    screen_rotate ++;
    if (screen_rotate == 4) { screen_rotate = 0; }
    
    switch(screen_rotate) {
      case 0: 
        my -= img.height;
        break;
      case 1: 
        mx += img.height;
        break;
      case 2: 
        my += img.height;
        break;
      case 3: 
        mx -= img.height;
        break;
     }
  }
  
  normalize_distribution();
  d1.distribute_pen_changes_according_to_percentages(display_line_count, pen_count);
  //surface.setSize(img.width, img.height);
  redraw();
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void set_even_distribution() {
  println("set_even_distribution");
  for (int p = 0; p<pen_count; p++) {
    pen_distribution[p] = display_line_count / pen_count;
    //println("pen_distribution[" + p + "] = " + pen_distribution[p]);
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void set_black_distribution() {
  println("set_black_distribution");
  for (int p=0; p<pen_count; p++) {
    pen_distribution[p] = 0;
    //println("pen_distribution[" + p + "] = " + pen_distribution[p]);
  }
  pen_distribution[0] = display_line_count;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void normalize_distribution() {
  float total = 0;

  println();
  //println("normalize_distribution");

  for (int p=0; p<pen_count; p++) {
    total = total + pen_distribution[p];
  }
  
  for (int p = 0; p<pen_count; p++) {
    pen_distribution[p] = display_line_count * pen_distribution[p] / total;
    print("Pen " + p + ", ");
    System.out.printf("%-4s", copic_sets[current_copic_set][p]);
    System.out.printf("%8.0f  ", pen_distribution[p]);
    
    // Display approximately one star for every percent of total
    for (int s = 0; s<PApplet.parseInt(pen_distribution[p]/total*100); s++) {
      print("*");
    }
    println();
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void loadInClass(String pfm_name){
  String className = this.getClass().getName() + "$" + pfm_name;
  try {
    cl = Class.forName(className);
  } catch (ClassNotFoundException e) { 
    println("\nError unknown PFM: " + className); 
  }
  
  ocl = null;
  if (cl != null) {
    try {
      // Get the constructor(s)
      java.lang.reflect.Constructor[] ctors = cl.getDeclaredConstructors();
      // Create an instance with the parent object as parameter (needed for inner classes)
      ocl = (pfm) ctors[0].newInstance(new Object[] { this });
    } catch (InstantiationException e) {
      println("Cannot create an instance of " + className);
    } catch (IllegalAccessException e) {
      println("Cannot access " + className + ": " + e.getMessage());
    } catch (Exception e) {
       // Lot of stuff can go wrong...
       e.printStackTrace();
    }
  }
  println("\nloaded PFM: " + className); 
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void mousePressed() {
  morgx = mouseX - mx; 
  morgy = mouseY - my; 
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void mouseDragged() {
  mx = mouseX-morgx; 
  my = mouseY-morgy; 
  redraw();
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
// This is the pfm interface, it contains the only methods the main code can call.
// As well as any variables that all pfm modules must have.
interface pfm {
  //public int x=0;
  public void pre_processing();
  public void find_path();
  public void post_processing();
  public void output_parameters();
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////
// A class to describe all the line segments
class botDrawing {
  private int line_count = 0;
  botLine[] lines = new botLine[10000000];
  String gcode_comment = "";
  
  public void botDrawing() {
  }

  public void render_last () {
    lines[line_count].render_with_copic();
  }
  
  public void render_all () {
    for (int i=1; i<line_count; i++) {
      lines[i].render_with_copic();
    }
  }
  
  public void render_some (int line_count) {
    for (int i=1; i<line_count; i++) {
      lines[i].render_with_copic();
    }
  }

  public void render_one_pen (int line_count, int pen) {
    int c = color(255, 0, 0);

    for (int i=1; i<line_count; i++) {
    //for (int i=line_count; i>1; i--) {
      if (lines[i].pen_number == pen) {
        lines[i].render_with_copic();
      }
    }
  }

  public void render_to_pdf (int line_count) {
    String pdfname = "gcode\\gcode_" + basefile_selected + ".pdf";
    PGraphics pdf = createGraphics(img.width, img.height, PDF, pdfname);
    pdf.beginDraw();
    pdf.background(255, 255, 255);
    for(int i=line_count; i>0; i--) {
      if(lines[i].pen_down) {
        int c = copic.get_original_color(copic_sets[current_copic_set][lines[i].pen_number]);
        pdf.stroke(c, 255);
        pdf.line(lines[i].x1, lines[i].y1, lines[i].x2, lines[i].y2);
      }
    }
    pdf.dispose();
    pdf.endDraw();
    println("PDF created:  " + pdfname);
  }

  public void render_each_pen_to_pdf (int line_count) {
    for (int p=0; p<=pen_count-1; p++) {
      String pdfname = "gcode\\gcode_" + basefile_selected + "_pen" + p + "_" + copic_sets[current_copic_set][p] + ".pdf";
      PGraphics pdf = createGraphics(img.width, img.height, PDF, pdfname);
      pdf.beginDraw();
      pdf.background(255, 255, 255);
      for (int i=line_count; i>0; i--) {
        if (lines[i].pen_down & lines[i].pen_number == p) {
          int c = copic.get_original_color(copic_sets[current_copic_set][lines[i].pen_number]);
          pdf.stroke(c, 255);
          pdf.line(lines[i].x1, lines[i].y1, lines[i].x2, lines[i].y2);
        }
      }
      pdf.dispose();
      pdf.endDraw();
      println("PDF created:  " + pdfname);
    }
  }
  
  public void set_pen_continuation_flags () {
    float prev_x = 123456.0f;
    float prev_y = 654321.0f;
    boolean prev_pen_down = false;
    int prev_pen_number = 123456;
    
    for (int i=1; i<line_count; i++) { 
 
      if (prev_x != lines[i].x1 || prev_y != lines[i].y1 || prev_pen_down != lines[i].pen_down  || prev_pen_number != lines[i].pen_number) {
        lines[i].pen_continuation = false;
      } else {
        lines[i].pen_continuation = true;
      }

      prev_x = lines[i].x2;
      prev_y = lines[i].y2;
      prev_pen_down = lines[i].pen_down;
      prev_pen_number = lines[i].pen_number;
    }
    println("set_pen_continuation_flags");
  }

  public void addline(int pen_number_, boolean pen_down_, float x1_, float y1_, float x2_, float y2_) {
    line_count++;
    lines[line_count] = new botLine (pen_down_, pen_number_, x1_, y1_, x2_, y2_);
  }
  
  public int get_line_count() {
    return line_count;
  }
  
  public void evenly_distribute_pen_changes (int line_count, int total_pens) {
    println("evenly_distribute_pen_changes");
    for (int i=1; i<=line_count; i++) {
      int cidx = (int)map(i - 1, 0, line_count, 1, total_pens);
      lines[i].pen_number = cidx;
      //println (i + "   " + lines[i].pen_number);
    }
  }

  public void distribute_pen_changes_according_to_percentages (int line_count, int total_pens) {
    int p = 0;
    float p_total = 0;
    
    for (int i=1; i<=line_count; i++) {
      if (i > pen_distribution[p] + p_total) {
        p_total = p_total + pen_distribution[p];
        p++;
      }
      if (p > total_pens - 1) {
        // Hacky fix for off by one error
        println("ERROR: distribute_pen_changes_according_to_percentages, p:  ", p);
        p = total_pens - 1;
      }
      lines[i].pen_number = p;
      //println (i + "   " + lines[i].pen_number);
    }
  }

}
///////////////////////////////////////////////////////////////////////////////////////////////////////
// A class to describe one line segment
//
// Because of a bug in processing.org the MULTIPLY blendMode does not take into account the alpha of
// either source or destination.  If this gets corrected, tweaks to the stroke alpha might be more 
// representative of a Copic marker.  Right now it over emphasizes the darkening when overlaps
// of the same pen occur.

class botLine {
  int pen_number;
  boolean pen_down;
  boolean pen_continuation;
  float x1;
  float y1;
  float x2;
  float y2;
  
  botLine(boolean pen_down_, int pen_number_, float x1_, float y1_, float x2_, float y2_) {
    pen_down = pen_down_;
    pen_continuation = false;
    pen_number = pen_number_;
    x1 = x1_;
    y1 = y1_;
    x2 = x2_;
    y2 = y2_;
  }

  public void render_with_copic() {
    if (pen_down) {
      int c = copic.get_original_color(copic_sets[current_copic_set][pen_number]);
      //stroke(c, 255-brightness(c));
      stroke(c);
      //strokeWeight(2);
      //blendMode(BLEND);
      blendMode(MULTIPLY);
      line(x1, y1, x2, y2);
    }
  }

}

///////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////
class intPoint {
  int x, y;
  
  intPoint(int x_, int y_) {
    x = x_;
    y = y_;
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
// Algorithm was developed by Jack Elton Bresenham in 1962
// http://en.wikipedia.org/wiki/Bresenham's_line_algorithm
// Traslated from pseudocode labled "Simplification" from the link above.
///////////////////////////////////////////////////////////////////////////////////////////////////////
public ArrayList <intPoint> bresenham(int x0, int y0, int x1, int y1) {
  int sx, sy;
  int err;
  int e2;
  ArrayList <intPoint> pnts = new ArrayList <intPoint>();

  int dx = abs(x1-x0);
  int dy = abs(y1-y0);
  if (x0 < x1) { sx = 1; } else { sx = -1; }
  if (y0 < y1) { sy = 1; } else { sy = -1; }
  err = dx-dy;
  while (true) {
    pnts.add(new intPoint(x0, y0));
    if ((x0 == x1) && (y0 == y1)) {
      return pnts;
    }
    e2 = 2*err;
    if (e2 > -dy) {
      err = err - dy;
      x0 = x0 + sx;
    }
    if (e2 < dx) {
      err = err + dx;
      y0 = y0 + sy;
    }
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
// Midpoint circle algorithm
// https://en.wikipedia.org/wiki/Midpoint_circle_algorithm
// I had to create 8 arrays of points then append them, because normaly order is not important.
///////////////////////////////////////////////////////////////////////////////////////////////////////
public ArrayList <intPoint> midpoint_circle(int x0, int y0, int radius) {
  ArrayList <intPoint> pnts = new ArrayList <intPoint>();
  
  ArrayList <intPoint> p1 = new ArrayList <intPoint>();
  ArrayList <intPoint> p2 = new ArrayList <intPoint>();
  ArrayList <intPoint> p3 = new ArrayList <intPoint>();
  ArrayList <intPoint> p4 = new ArrayList <intPoint>();
  ArrayList <intPoint> p5 = new ArrayList <intPoint>();
  ArrayList <intPoint> p6 = new ArrayList <intPoint>();
  ArrayList <intPoint> p7 = new ArrayList <intPoint>();
  ArrayList <intPoint> p8 = new ArrayList <intPoint>();
  
  int x = radius;
  int y = 0;
  int err = 0;

  while (x >= y) {
    p1.add(new intPoint(x0 + x, y0 + y));
    p2.add(new intPoint(x0 + y, y0 + x));
    p3.add(new intPoint(x0 - y, y0 + x));
    p4.add(new intPoint(x0 - x, y0 + y));
    p5.add(new intPoint(x0 - x, y0 - y));
    p6.add(new intPoint(x0 - y, y0 - x));
    p7.add(new intPoint(x0 + y, y0 - x));
    p8.add(new intPoint(x0 + x, y0 - y));

    if (err <= 0) {
        y += 1;
        err += 2*y + 1;
    }
    if (err > 0) {
        x -= 1;
        err -= 2*x + 1;
    }
  }
  
  for (intPoint p : p1) { pnts.add(p); }
  for (intPoint p : p2) { pnts.add(p); }
  for (intPoint p : p3) { pnts.add(p); }
  for (intPoint p : p4) { pnts.add(p); }
  for (intPoint p : p5) { pnts.add(p); }
  for (intPoint p : p6) { pnts.add(p); }
  for (intPoint p : p7) { pnts.add(p); }
  for (intPoint p : p8) { pnts.add(p); }
  return pnts;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
  public void bresenham_lighten(int x0, int y0, int x1, int y1, int adjustbrightness) {
    ArrayList <intPoint> pnts;
  
    pnts = bresenham(x0, y0, x1, y1);
    for (intPoint p : pnts) {
      lighten_one_pixel(adjustbrightness * 5, p.x, p.y);
    }
  }

///////////////////////////////////////////////////////////////////////////////////////////////////////
// Regex used in sublime to clean up html from:
// Source data:  https://imaginationinternationalinc.com/copic/store/color-picker/
//
// ^.*color:
// ; cursor.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*<h5>
// </h5>\n.*<p>
// </p>\n.*clearfix.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n.*\n
// ^(.*?),(.*?),(.*)
// sketch_color.put("$2"), color($1));  sketch_name.put("$2"), "$3");


///////////////////////////////////////////////////////////////////////////////////////////////////////
public void copic_alpha_simulator() {
  int[] p = new int[5]; 
  p[0] = copic.get_original_color("N1");
  p[1] = copic.get_original_color("N3");
  p[2] = copic.get_original_color("N5");
  p[3] = copic.get_original_color("N7");
  p[4] = copic.get_original_color("100");
  
  int alpha = 210;
  int pen_off=200;
  int off=30;
  
  for (int pen=0; pen<5; pen++) {
    for (int x=0; x<5; x++) {
      //fill(p[pen], alpha);  rect(pen*150+10, pen*off+x*pen_off, 500, 80);
      stroke(p[pen], alpha);
      strokeWeight(50);
      fill(p[4], 50);
      line(pen*150+10, pen*off+x*pen_off, pen*150+10+500, pen*off+x*pen_off);
    }
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void test_draw_closest_copic_color() {
  noStroke();
  
  for (int i = 0; i < 255; i++) {
    colorMode(HSB, 255);
    int c1 = color(i, mouseX, mouseY);
    //color c1 = color(mouseX, i, mouseY);
    //color c1 = color(mouseX, mouseY, i);
    fill(c1);
    rect(i*5, 0, 5, 100);
        
    String p = copic.get_closest_original(c1);
    //println(p + "   " + c.get_original_name(p));
    int c2 = copic.get_original_color(p);
    fill(c2);
    rect(i*5, 105, 5, 100);
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
class Copix {
  HashMap <String, Integer> sketch_color;
  HashMap <String, String> sketch_name;
  HashMap <String, Integer> original_color;
  HashMap <String, String> original_name;
 
  Copix() {
    sketch_color = new HashMap <String, Integer> ();
    sketch_name = new HashMap <String, String> ();
    original_color = new HashMap <String, Integer> ();
    original_name = new HashMap <String, String> ();
    
    sketch_color.put("0", color(0xffffffff));  sketch_name.put("0", "Colorless Blender");
    sketch_color.put("100", color(0xff312b2b));  sketch_name.put("100", "Black");
    sketch_color.put("110", color(0xff030708));  sketch_name.put("110", "Special Black");
    sketch_color.put("B0000", color(0xfff0f9fe));  sketch_name.put("B0000", "Pale Celestine");
    sketch_color.put("B000", color(0xffe6f4f5));  sketch_name.put("B000", "Pale Porcelain Blue");
    sketch_color.put("B00", color(0xffddf0f4));  sketch_name.put("B00", "Frost Blue");
    sketch_color.put("B01", color(0xffd6eef2));  sketch_name.put("B01", "Mint Blue");
    sketch_color.put("B02", color(0xffb3e3f1));  sketch_name.put("B02", "Robin&#39;s Egg Blue");
    sketch_color.put("B04", color(0xff73cfe6));  sketch_name.put("B04", "Tahitian Blue");
    sketch_color.put("B05", color(0xff40c5e6));  sketch_name.put("B05", "Process Blue");
    sketch_color.put("B06", color(0xff00b3e6));  sketch_name.put("B06", "Peacok Blue");
    sketch_color.put("B12", color(0xffc8e6f0));  sketch_name.put("B12", "Ice Blue");
    sketch_color.put("B14", color(0xff71cfeb));  sketch_name.put("B14", "Light Blue");
    sketch_color.put("B16", color(0xff00bcea));  sketch_name.put("B16", "Cyanine Blue");
    sketch_color.put("B18", color(0xff1d8acb));  sketch_name.put("B18", "Lapis Lazuli");
    sketch_color.put("B21", color(0xffdbedf9));  sketch_name.put("B21", "Baby Blue");
    sketch_color.put("B23", color(0xff92c2e8));  sketch_name.put("B23", "Phthalo Blue");
    sketch_color.put("B24", color(0xff8acef3));  sketch_name.put("B24", "Sky");
    sketch_color.put("B26", color(0xff65b3e3));  sketch_name.put("B26", "Cobalt Blue");
    sketch_color.put("B28", color(0xff196db6));  sketch_name.put("B28", "Royal Blue");
    sketch_color.put("B29", color(0xff0177c1));  sketch_name.put("B29", "Ultramarine");
    sketch_color.put("B32", color(0xffe2eff7));  sketch_name.put("B32", "Pale Blue");
    sketch_color.put("B34", color(0xff82c3ed));  sketch_name.put("B34", "Manganese Blue");
    sketch_color.put("B37", color(0xff156fa4));  sketch_name.put("B37", "Antwerp Blue");
    sketch_color.put("B39", color(0xff2b64a9));  sketch_name.put("B39", "Prussian Blue");
    sketch_color.put("B41", color(0xffe2f0fb));  sketch_name.put("B41", "Powder Blue");
    sketch_color.put("B45", color(0xff75c0ea));  sketch_name.put("B45", "Smoky Blue");
    sketch_color.put("B52", color(0xffadcddc));  sketch_name.put("B52", "Soft Greenish Blue");
    sketch_color.put("B60", color(0xffdae1f3));  sketch_name.put("B60", "Pale Blue Gray");
    sketch_color.put("B63", color(0xffa7bbe0));  sketch_name.put("B63", "Light Hydrangea");
    sketch_color.put("B66", color(0xff6888c5));  sketch_name.put("B66", "Clematis");
    sketch_color.put("B69", color(0xff2165ae));  sketch_name.put("B69", "Stratospheric Blue");
    sketch_color.put("B79", color(0xff3b479d));  sketch_name.put("B79", "Iris");
    sketch_color.put("B91", color(0xffd5e2eb));  sketch_name.put("B91", "Pale Grayish Blue");
    sketch_color.put("B93", color(0xff95c1da));  sketch_name.put("B93", "Light Crockery Blue");
    sketch_color.put("B95", color(0xff74a7c6));  sketch_name.put("B95", "Light Grayish Cobalt");
    sketch_color.put("B97", color(0xff457a9a));  sketch_name.put("B97", "Night Blue");
    sketch_color.put("B99", color(0xff0f547e));  sketch_name.put("B99", "Agate");
    sketch_color.put("BG0000", color(0xffeff8f3));  sketch_name.put("BG0000", "Snow Green");
    sketch_color.put("BG000", color(0xffe5f4ed));  sketch_name.put("BG000", "Pale Aqua");
    sketch_color.put("BG01", color(0xffc7e6fa));  sketch_name.put("BG01", "Aqua Blue");
    sketch_color.put("BG02", color(0xffc6e8ea));  sketch_name.put("BG02", "New Blue");
    sketch_color.put("BG05", color(0xff83d2e1));  sketch_name.put("BG05", "Holiday Blue");
    sketch_color.put("BG07", color(0xff1db8ce));  sketch_name.put("BG07", "Petroleum Blue");
    sketch_color.put("BG09", color(0xff01b1c9));  sketch_name.put("BG09", "Blue Green");
    sketch_color.put("BG10", color(0xffdcf0ef));  sketch_name.put("BG10", "Cool Shadow");
    sketch_color.put("BG11", color(0xffceebf1));  sketch_name.put("BG11", "Moon White");
    sketch_color.put("BG13", color(0xffc4e7e9));  sketch_name.put("BG13", "Mint Green");
    sketch_color.put("BG15", color(0xffa0d9d2));  sketch_name.put("BG15", "Aqua");
    sketch_color.put("BG18", color(0xff37c0b0));  sketch_name.put("BG18", "Teal Blue");
    sketch_color.put("BG23", color(0xffbde5dd));  sketch_name.put("BG23", "Coral Sea");
    sketch_color.put("BG32", color(0xffbce2d7));  sketch_name.put("BG32", "Aqua Mint");
    sketch_color.put("BG34", color(0xffa3dad7));  sketch_name.put("BG34", "Horizon Green");
    sketch_color.put("BG45", color(0xffafdfdf));  sketch_name.put("BG45", "Nile Blue");
    sketch_color.put("BG49", color(0xff00b6b9));  sketch_name.put("BG49", "Duck Blue");
    sketch_color.put("BG53", color(0xffaccfd1));  sketch_name.put("BG53", "Ice Mint");
    sketch_color.put("BG57", color(0xff64bebe));  sketch_name.put("BG57", "Sketch Jasper");
    sketch_color.put("BG70", color(0xffdaecee));  sketch_name.put("BG70", "Ocean Mist");
    sketch_color.put("BG72", color(0xff74b8bb));  sketch_name.put("BG72", "Ice Ocean");
    sketch_color.put("BG75", color(0xff59918e));  sketch_name.put("BG75", "Abyss Green");
    sketch_color.put("BG78", color(0xff49706b));  sketch_name.put("BG78", "Bronze");
    sketch_color.put("BG90", color(0xffe8ede7));  sketch_name.put("BG90", "Sketch Gray Sky");
    sketch_color.put("BG93", color(0xffbac1b9));  sketch_name.put("BG93", "Green Gray");
    sketch_color.put("BG96", color(0xff81a291));  sketch_name.put("BG96", "Bush");
    sketch_color.put("BG99", color(0xff6e9b87));  sketch_name.put("BG99", "Flagstone Blue");
    sketch_color.put("BV0000", color(0xffeae7f2));  sketch_name.put("BV0000", "Sketch Pale Thistle");
    sketch_color.put("BV000", color(0xffeae7f2));  sketch_name.put("BV000", "Iridescent Mauve");
    sketch_color.put("BV00", color(0xffe0dced));  sketch_name.put("BV00", "Mauve Shadow");
    sketch_color.put("BV01", color(0xffc4c9e6));  sketch_name.put("BV01", "Viola");
    sketch_color.put("BV02", color(0xffaab8db));  sketch_name.put("BV02", "Prune");
    sketch_color.put("BV04", color(0xff7c97ce));  sketch_name.put("BV04", "Blue Berry");
    sketch_color.put("BV08", color(0xff9d7eb9));  sketch_name.put("BV08", "Blue Violet");
    sketch_color.put("BV11", color(0xffd4d2e8));  sketch_name.put("BV11", "Soft Violet");
    sketch_color.put("BV13", color(0xff8491c8));  sketch_name.put("BV13", "Hydrangea Blue");
    sketch_color.put("BV17", color(0xff6e84bd));  sketch_name.put("BV17", "Deep Reddish Blue");
    sketch_color.put("BV20", color(0xffcfdbf1));  sketch_name.put("BV20", "Dull Lavender");
    sketch_color.put("BV23", color(0xffb1c0dd));  sketch_name.put("BV23", "Grayish Lavender");
    sketch_color.put("BV25", color(0xff8184a7));  sketch_name.put("BV25", "Grayish Violet");
    sketch_color.put("BV29", color(0xff384558));  sketch_name.put("BV29", "Slate");
    sketch_color.put("BV31", color(0xffeae7f2));  sketch_name.put("BV31", "Pale Lavender");
    sketch_color.put("BV34", color(0xff9fa7bc));  sketch_name.put("BV34", "Sketch Bluebell");
    sketch_color.put("C00", color(0xffe8f0f3));  sketch_name.put("C00", "Cool Gray");
    sketch_color.put("C0", color(0xffe0e7ed));  sketch_name.put("C0", "Cool Gray");
    sketch_color.put("C10", color(0xff202b31));  sketch_name.put("C10", "Cool Gray");
    sketch_color.put("C1", color(0xffdae3e8));  sketch_name.put("C1", "Cool Gray No. 1");
    sketch_color.put("C2", color(0xffccd7dd));  sketch_name.put("C2", "Cool Gray");
    sketch_color.put("C3", color(0xffc1ccd2));  sketch_name.put("C3", "Cool Gray No. 3");
    sketch_color.put("C4", color(0xffa6b4bd));  sketch_name.put("C4", "Cool Gray");
    sketch_color.put("C5", color(0xff92a0ab));  sketch_name.put("C5", "Cool Gray No. 5");
    sketch_color.put("C6", color(0xff7b8c96));  sketch_name.put("C6", "Cool Gray");
    sketch_color.put("C7", color(0xff637079));  sketch_name.put("C7", "Cool Gray No. 7");
    sketch_color.put("C8", color(0xff535d66));  sketch_name.put("C8", "Cool Gray");
    sketch_color.put("C9", color(0xff3c474d));  sketch_name.put("C9", "Cool Gray");
    sketch_color.put("E0000", color(0xfffffaf4));  sketch_name.put("E0000", "Floral White");
    sketch_color.put("E000", color(0xfffef5ee));  sketch_name.put("E000", "Pale Fruit Pink");
    sketch_color.put("E00", color(0xfffdf3ea));  sketch_name.put("E00", "Cotton Pearl");
    sketch_color.put("E01", color(0xffffeee4));  sketch_name.put("E01", "Pink Flamingo");
    sketch_color.put("E02", color(0xfffeece0));  sketch_name.put("E02", "Fruit Pink");
    sketch_color.put("E04", color(0xffe4bcc4));  sketch_name.put("E04", "Lipstick Natural");
    sketch_color.put("E07", color(0xffcc816a));  sketch_name.put("E07", "Light Mahogany");
    sketch_color.put("E08", color(0xffca6553));  sketch_name.put("E08", "Brown");
    sketch_color.put("E09", color(0xffd96a4f));  sketch_name.put("E09", "Burnt Sienna");
    sketch_color.put("E11", color(0xfffee9d6));  sketch_name.put("E11", "Barley Beige");
    sketch_color.put("E13", color(0xffe9c5af));  sketch_name.put("E13", "Light Suntan");
    sketch_color.put("E15", color(0xfffbbb8d));  sketch_name.put("E15", "Dark Suntan");
    sketch_color.put("E17", color(0xffb85f57));  sketch_name.put("E17", "Reddish Brass");
    sketch_color.put("E18", color(0xff88534d));  sketch_name.put("E18", "Copper");
    sketch_color.put("E19", color(0xffc45238));  sketch_name.put("E19", "Redwood");
    sketch_color.put("E21", color(0xfffde2c7));  sketch_name.put("E21", "Soft Sun");
    sketch_color.put("E23", color(0xffeccab1));  sketch_name.put("E23", "Hazelnut");
    sketch_color.put("E25", color(0xffd2a482));  sketch_name.put("E25", "Caribe Cocoa");
    sketch_color.put("E27", color(0xff997663));  sketch_name.put("E27", "Milk Chocolate");
    sketch_color.put("E29", color(0xff884636));  sketch_name.put("E29", "Burnt Umber");
    sketch_color.put("E30", color(0xfff7f0d6));  sketch_name.put("E30", "Bisque");
    sketch_color.put("E31", color(0xfff2e6ce));  sketch_name.put("E31", "Brick Beige");
    sketch_color.put("E33", color(0xfff3d2b1));  sketch_name.put("E33", "Sand");
    sketch_color.put("E34", color(0xfff0caa6));  sketch_name.put("E34", "Toast");
    sketch_color.put("E35", color(0xffe6c3a3));  sketch_name.put("E35", "Chamois");
    sketch_color.put("E37", color(0xffcc9159));  sketch_name.put("E37", "Sepia");
    sketch_color.put("E39", color(0xffc5743f));  sketch_name.put("E39", "Leather");
    sketch_color.put("E40", color(0xfff2e8dc));  sketch_name.put("E40", "Brick White");
    sketch_color.put("E41", color(0xfffef1e1));  sketch_name.put("E41", "Pearl White");
    sketch_color.put("E42", color(0xfff3ead9));  sketch_name.put("E42", "Sand White");
    sketch_color.put("E43", color(0xffe8dabd));  sketch_name.put("E43", "Dull Ivory");
    sketch_color.put("E44", color(0xffc5b9a9));  sketch_name.put("E44", "Clay");
    sketch_color.put("E47", color(0xff8a6e59));  sketch_name.put("E47", "Dark Brown");
    sketch_color.put("E49", color(0xff634c3c));  sketch_name.put("E49", "Dark Bark");
    sketch_color.put("E50", color(0xfff4ebf0));  sketch_name.put("E50", "Egg Shell");
    sketch_color.put("E51", color(0xfffeecd6));  sketch_name.put("E51", "Milky White");
    sketch_color.put("E53", color(0xfff3e6c3));  sketch_name.put("E53", "Raw Silk");
    sketch_color.put("E55", color(0xfff1dfb9));  sketch_name.put("E55", "Light Camel");
    sketch_color.put("E57", color(0xffb18558));  sketch_name.put("E57", "Light Walnut");
    sketch_color.put("E59", color(0xff9a7f6c));  sketch_name.put("E59", "Walnut");
    sketch_color.put("E70", color(0xffefeae6));  sketch_name.put("E70", "Ash Rose");
    sketch_color.put("E71", color(0xffe2d7d3));  sketch_name.put("E71", "Champagne");
    sketch_color.put("E74", color(0xffa1847c));  sketch_name.put("E74", "Cocoa Brown");
    sketch_color.put("E77", color(0xff7f604e));  sketch_name.put("E77", "Maroon");
    sketch_color.put("E79", color(0xff4a2c22));  sketch_name.put("E79", "Cashew");
    sketch_color.put("E81", color(0xfff0e6c2));  sketch_name.put("E81", "Ivory");
    sketch_color.put("E84", color(0xffae9f80));  sketch_name.put("E84", "- Sketch Khaki");
    sketch_color.put("E87", color(0xff6f604d));  sketch_name.put("E87", "Fig");
    sketch_color.put("E89", color(0xff5a4939));  sketch_name.put("E89", "- Sketch Pecan");
    sketch_color.put("E93", color(0xfffed2b9));  sketch_name.put("E93", "Tea Rose");
    sketch_color.put("E95", color(0xfffcbc7e));  sketch_name.put("E95", "Tea Orange");
    sketch_color.put("E97", color(0xffed9c5d));  sketch_name.put("E97", "Deep Orange");
    sketch_color.put("E99", color(0xffb46034));  sketch_name.put("E99", "Baked Clay");
    sketch_color.put("FB2", color(0xff058fd0));  sketch_name.put("FB2", "Fluorescent Dull Blue");
    sketch_color.put("FBG2", color(0xff62cbe8));  sketch_name.put("FBG2", "Fluorescent Dull Blue Green");
    sketch_color.put("FRV1", color(0xfff5a3c7));  sketch_name.put("FRV1", "Fluorescent Pink");
    sketch_color.put("FV2", color(0xff7f74b6));  sketch_name.put("FV2", "Fluorescent Dull Violet");
    sketch_color.put("FY1", color(0xfffff697));  sketch_name.put("FY1", "Fluorescent Yellow Orange");
    sketch_color.put("FYG1", color(0xff9ecd43));  sketch_name.put("FYG1", "Fluorescent Yellow");
    sketch_color.put("FYG2", color(0xff9ecd43));  sketch_name.put("FYG2", "Fluorescent Dull Yellow Green");
    sketch_color.put("FYR1", color(0xfffecc99));  sketch_name.put("FYR1", "Fluorescent Orange");
    sketch_color.put("G0000", color(0xfff1f7f3));  sketch_name.put("G0000", "Crystal Opal");
    sketch_color.put("G000", color(0xffeaf5ed));  sketch_name.put("G000", "Pale Green");
    sketch_color.put("G00", color(0xffe3f2ed));  sketch_name.put("G00", "Jade Green");
    sketch_color.put("G02", color(0xffcfe8d3));  sketch_name.put("G02", "Spectrum Green");
    sketch_color.put("G03", color(0xffb6da9c));  sketch_name.put("G03", "Meadow Green");
    sketch_color.put("G05", color(0xff69c07b));  sketch_name.put("G05", "Emerald Green");
    sketch_color.put("G07", color(0xff7bc576));  sketch_name.put("G07", "Nile Green");
    sketch_color.put("G09", color(0xff7ac465));  sketch_name.put("G09", "Veronese Green");
    sketch_color.put("G12", color(0xffd2e8c4));  sketch_name.put("G12", "Sea Green");
    sketch_color.put("G14", color(0xff97cf90));  sketch_name.put("G14", "Apple Green");
    sketch_color.put("G16", color(0xff60c198));  sketch_name.put("G16", "Malachite");
    sketch_color.put("G17", color(0xff14b37d));  sketch_name.put("G17", "Forest Green");
    sketch_color.put("G19", color(0xff2db98a));  sketch_name.put("G19", "Bright Parrot Green");
    sketch_color.put("G20", color(0xffedf6db));  sketch_name.put("G20", "Wax White");
    sketch_color.put("G21", color(0xffc4e4cd));  sketch_name.put("G21", "Lime Green");
    sketch_color.put("G24", color(0xffc3e0b4));  sketch_name.put("G24", "Willow");
    sketch_color.put("G28", color(0xff119462));  sketch_name.put("G28", "Ocean Green");
    sketch_color.put("G29", color(0xff197c5d));  sketch_name.put("G29", "Pine Tree Green");
    sketch_color.put("G40", color(0xffe4f1df));  sketch_name.put("G40", "Dim Green");
    sketch_color.put("G43", color(0xffd7e7a8));  sketch_name.put("G43", "Various Pistachio");
    sketch_color.put("G46", color(0xff579e74));  sketch_name.put("G46", "Sketch Mistletoe");
    sketch_color.put("G82", color(0xffccdab9));  sketch_name.put("G82", "Spring Dim Green");
    sketch_color.put("G85", color(0xff9dc3aa));  sketch_name.put("G85", "Verdigris");
    sketch_color.put("G94", color(0xff98a786));  sketch_name.put("G94", "Grayish Olive");
    sketch_color.put("G99", color(0xff5f7e3a));  sketch_name.put("G99", "Olive");
    sketch_color.put("N0", color(0xffeceeed));  sketch_name.put("N0", "Neutral Gray");
    sketch_color.put("N10", color(0xff312f30));  sketch_name.put("N10", "Neutral Gray");
    sketch_color.put("N1", color(0xffe2e3e5));  sketch_name.put("N1", "Neutral Gray");
    sketch_color.put("N2", color(0xffdadbdd));  sketch_name.put("N2", "Neutral Gray");
    sketch_color.put("N3", color(0xffd1d2d4));  sketch_name.put("N3", "Neutral Gray");
    sketch_color.put("N4", color(0xffbcbdc1));  sketch_name.put("N4", "Neutral Gray");
    sketch_color.put("N5", color(0xffa8a9ad));  sketch_name.put("N5", "Neutral Gray");
    sketch_color.put("N6", color(0xff949599));  sketch_name.put("N6", "Neutral Gray");
    sketch_color.put("N7", color(0xff77787c));  sketch_name.put("N7", "Neutral Gray");
    sketch_color.put("N8", color(0xff636466));  sketch_name.put("N8", "Neutral Gray");
    sketch_color.put("N9", color(0xff4c4d4f));  sketch_name.put("N9", "Neutral Gray");
    sketch_color.put("R0000", color(0xfffef3ef));  sketch_name.put("R0000", "Pink Beryl");
    sketch_color.put("R000", color(0xfffef0e7));  sketch_name.put("R000", "Cherry White");
    sketch_color.put("R00", color(0xfffeeae1));  sketch_name.put("R00", "Pinkish White");
    sketch_color.put("R01", color(0xfffde0d8));  sketch_name.put("R01", "Pinkish Vanilla");
    sketch_color.put("R02", color(0xfffdd3c7));  sketch_name.put("R02", "Rose Salmon");
    sketch_color.put("R05", color(0xfff6927b));  sketch_name.put("R05", "Salmon Red");
    sketch_color.put("R08", color(0xfff26754));  sketch_name.put("R08", "Vermilion");
    sketch_color.put("R11", color(0xfffde1d5));  sketch_name.put("R11", "Pale Cherry Pink");
    sketch_color.put("R12", color(0xfffcd3c1));  sketch_name.put("R12", "Light Tea Rose");
    sketch_color.put("R14", color(0xfff59b92));  sketch_name.put("R14", "Light Rouge");
    sketch_color.put("R17", color(0xfff4846c));  sketch_name.put("R17", "Lipstick Orange");
    sketch_color.put("R20", color(0xfffcd7cf));  sketch_name.put("R20", "Blush");
    sketch_color.put("R21", color(0xfffac1b6));  sketch_name.put("R21", "Sardonyx");
    sketch_color.put("R22", color(0xfff8b7b1));  sketch_name.put("R22", "Light Prawn");
    sketch_color.put("R24", color(0xfff27579));  sketch_name.put("R24", "Prawn");
    sketch_color.put("R27", color(0xfff15062));  sketch_name.put("R27", "Cadmium Red");
    sketch_color.put("R29", color(0xffed174b));  sketch_name.put("R29", "Lipstick Red");
    sketch_color.put("R30", color(0xfffce3df));  sketch_name.put("R30", "Pale Yellowish Pink");
    sketch_color.put("R32", color(0xfffac1ba));  sketch_name.put("R32", "Peach");
    sketch_color.put("R35", color(0xfff27185));  sketch_name.put("R35", "Coral");
    sketch_color.put("R37", color(0xffe86c74));  sketch_name.put("R37", "Carmine");
    sketch_color.put("R39", color(0xffcb487a));  sketch_name.put("R39", "Garnet");
    sketch_color.put("R43", color(0xffee848e));  sketch_name.put("R43", "Bougainvillaea");
    sketch_color.put("R46", color(0xffe04d69));  sketch_name.put("R46", "Strong Red");
    sketch_color.put("R56", color(0xffd27c95));  sketch_name.put("R56", "Currant");
    sketch_color.put("R59", color(0xffb74f70));  sketch_name.put("R59", "Cardinal");
    sketch_color.put("R81", color(0xfff1c8d6));  sketch_name.put("R81", "Rose Pink");
    sketch_color.put("R83", color(0xfff19cb9));  sketch_name.put("R83", "Rose Mist");
    sketch_color.put("R85", color(0xffd36a93));  sketch_name.put("R85", "Rose Red");
    sketch_color.put("R89", color(0xff7d2b42));  sketch_name.put("R89", "Dark Red");
    sketch_color.put("RV0000", color(0xfff2eaf5));  sketch_name.put("RV0000", "Evening Primrose");
    sketch_color.put("RV000", color(0xfff4e2ee));  sketch_name.put("RV000", "Pale Purple");
    sketch_color.put("RV00", color(0xfff1daea));  sketch_name.put("RV00", "Water Lily");
    sketch_color.put("RV02", color(0xfffad5e6));  sketch_name.put("RV02", "Sugared Almond Pink");
    sketch_color.put("RV04", color(0xfff6a3bf));  sketch_name.put("RV04", "Shock  Pink");
    sketch_color.put("RV06", color(0xfff386af));  sketch_name.put("RV06", "Cerise");
    sketch_color.put("RV09", color(0xffe171ac));  sketch_name.put("RV09", "Fuchsia");
    sketch_color.put("RV10", color(0xfffdecf4));  sketch_name.put("RV10", "Pale Pink");
    sketch_color.put("RV11", color(0xfffbd6dd));  sketch_name.put("RV11", "Pink");
    sketch_color.put("RV13", color(0xfff9c9d7));  sketch_name.put("RV13", "Tender Pink");
    sketch_color.put("RV14", color(0xfff495b7));  sketch_name.put("RV14", "Begonia Pink");
    sketch_color.put("RV17", color(0xffdb7eb3));  sketch_name.put("RV17", "Deep Magenta");
    sketch_color.put("RV19", color(0xffd268aa));  sketch_name.put("RV19", "Red Violet");
    sketch_color.put("RV21", color(0xfffde8e7));  sketch_name.put("RV21", "Light Pink");
    sketch_color.put("RV23", color(0xfff8bac9));  sketch_name.put("RV23", "Pure Pink");
    sketch_color.put("RV25", color(0xfff493be));  sketch_name.put("RV25", "Dog Rose Flower");
    sketch_color.put("RV29", color(0xffef4880));  sketch_name.put("RV29", "Crimson");
    sketch_color.put("RV32", color(0xfffad3ce));  sketch_name.put("RV32", "Shadow Pink");
    sketch_color.put("RV34", color(0xfff9afae));  sketch_name.put("RV34", "Dark Pink");
    sketch_color.put("RV42", color(0xfff8bbb6));  sketch_name.put("RV42", "Salmon Pink");
    sketch_color.put("RV52", color(0xfff9cade));  sketch_name.put("RV52", "Various Cotton Candy");
    sketch_color.put("RV55", color(0xffe9a5ca));  sketch_name.put("RV55", "Hollyhock");
    sketch_color.put("RV63", color(0xffd09dae));  sketch_name.put("RV63", "Begonia");
    sketch_color.put("RV66", color(0xffb86a84));  sketch_name.put("RV66", "Raspberry");
    sketch_color.put("RV69", color(0xff8b576e));  sketch_name.put("RV69", "Peony");
    sketch_color.put("RV91", color(0xffe6d4e2));  sketch_name.put("RV91", "Garyish Cherry");
    sketch_color.put("RV93", color(0xffe7b6cc));  sketch_name.put("RV93", "Smokey Purple");
    sketch_color.put("RV95", color(0xffb684a1));  sketch_name.put("RV95", "Baby Blossoms");
    sketch_color.put("RV99", color(0xff5a4858));  sketch_name.put("RV99", "Argyle Purple");
    sketch_color.put("T0", color(0xffeceeed));  sketch_name.put("T0", "Toner Gray");
    sketch_color.put("T10", color(0xff322e2d));  sketch_name.put("T10", "Toner Gray");
    sketch_color.put("T1", color(0xffeaeae8));  sketch_name.put("T1", "Toner Gray");
    sketch_color.put("T2", color(0xffe0e0de));  sketch_name.put("T2", "Toner Gray");
    sketch_color.put("T3", color(0xffd1d2cc));  sketch_name.put("T3", "Toner Gray");
    sketch_color.put("T4", color(0xffbcbbb9));  sketch_name.put("T4", "Toner Gray");
    sketch_color.put("T5", color(0xffa8a7a3));  sketch_name.put("T5", "Toner Gray");
    sketch_color.put("T6", color(0xff949590));  sketch_name.put("T6", "Toner Gray");
    sketch_color.put("T7", color(0xff777674));  sketch_name.put("T7", "Toner Gray");
    sketch_color.put("T8", color(0xff63645f));  sketch_name.put("T8", "Toner Gray");
    sketch_color.put("T9", color(0xff4c4b49));  sketch_name.put("T9", "Toner Gray");
    sketch_color.put("V0000", color(0xfff0edf6));  sketch_name.put("V0000", "Rose Quartz");
    sketch_color.put("V000", color(0xffe9e5f3));  sketch_name.put("V000", "Pale Heath");
    sketch_color.put("V01", color(0xffe4c1d9));  sketch_name.put("V01", "Heath");
    sketch_color.put("V04", color(0xffe6aace));  sketch_name.put("V04", "Lilac");
    sketch_color.put("V05", color(0xffe2a6ca));  sketch_name.put("V05", "Azalea");
    sketch_color.put("V06", color(0xffce95c2));  sketch_name.put("V06", "Lavender");
    sketch_color.put("V09", color(0xff8754a1));  sketch_name.put("V09", "Violet");
    sketch_color.put("V12", color(0xffeed7e9));  sketch_name.put("V12", "Pale Lilac");
    sketch_color.put("V15", color(0xffd3a6cd));  sketch_name.put("V15", "Mallow");
    sketch_color.put("V17", color(0xffa092c7));  sketch_name.put("V17", "Amethyst");
    sketch_color.put("V20", color(0xffe2e0ed));  sketch_name.put("V20", "Wisteria");
    sketch_color.put("V22", color(0xffb2b1d0));  sketch_name.put("V22", "Sketch Ash Lavender");
    sketch_color.put("V25", color(0xff857fad));  sketch_name.put("V25", "Pale Blackberry");
    sketch_color.put("V28", color(0xff6b668e));  sketch_name.put("V28", "Sketch Eggplant");
    sketch_color.put("V91", color(0xffe8c4d0));  sketch_name.put("V91", "Pale Grape");
    sketch_color.put("V93", color(0xffe5c1db));  sketch_name.put("V93", "Early Grape");
    sketch_color.put("V95", color(0xffb77ca8));  sketch_name.put("V95", "Light Grape");
    sketch_color.put("V99", color(0xff524358));  sketch_name.put("V99", "Aubergine");
    sketch_color.put("W00", color(0xfff3f3eb));  sketch_name.put("W00", "Warm Gray");
    sketch_color.put("W0", color(0xffecece4));  sketch_name.put("W0", "Warm Gray");
    sketch_color.put("W10", color(0xff302f2b));  sketch_name.put("W10", "Warm Gray");
    sketch_color.put("W1", color(0xffe7e7df));  sketch_name.put("W1", "Warm Gray No. 1");
    sketch_color.put("W2", color(0xffddddd5));  sketch_name.put("W2", "Warm Gray");
    sketch_color.put("W3", color(0xffd2d2ca));  sketch_name.put("W3", "Warm Gray No. 3");
    sketch_color.put("W4", color(0xffbcbdb7));  sketch_name.put("W4", "Warm Gray");
    sketch_color.put("W5", color(0xffa8a9a4));  sketch_name.put("W5", "Warm Gray No. 5");
    sketch_color.put("W6", color(0xff94958f));  sketch_name.put("W6", "Warm Gray");
    sketch_color.put("W7", color(0xff777873));  sketch_name.put("W7", "Warm Gray No. 7");
    sketch_color.put("W8", color(0xff63645f));  sketch_name.put("W8", "Warm Gray");
    sketch_color.put("W9", color(0xff4c4d48));  sketch_name.put("W9", "Warm Gray");
    sketch_color.put("Y0000", color(0xfffefef4));  sketch_name.put("Y0000", "Yellow Fluorite");
    sketch_color.put("Y000", color(0xfffffce9));  sketch_name.put("Y000", "Pale Lemon");
    sketch_color.put("Y00", color(0xfffefddf));  sketch_name.put("Y00", "Barium Yellow");
    sketch_color.put("Y02", color(0xfff6f396));  sketch_name.put("Y02", "Canary Yellow");
    sketch_color.put("Y04", color(0xffede556));  sketch_name.put("Y04", "Acacia");
    sketch_color.put("Y06", color(0xfffef56c));  sketch_name.put("Y06", "Yellow");
    sketch_color.put("Y08", color(0xfffef200));  sketch_name.put("Y08", "Acid Yellow");
    sketch_color.put("Y11", color(0xfffffbcc));  sketch_name.put("Y11", "Pale Yellow");
    sketch_color.put("Y13", color(0xfffbf7ae));  sketch_name.put("Y13", "Lemon Yellow");
    sketch_color.put("Y15", color(0xfffee96c));  sketch_name.put("Y15", "Cadmium Yellow");
    sketch_color.put("Y17", color(0xffffe455));  sketch_name.put("Y17", "Golden Yellow");
    sketch_color.put("Y18", color(0xfffeed55));  sketch_name.put("Y18", "Lightning Yellow");
    sketch_color.put("Y19", color(0xffffe93e));  sketch_name.put("Y19", "Napoli Yellow");
    sketch_color.put("Y21", color(0xffffeec2));  sketch_name.put("Y21", "Buttercup Yellow");
    sketch_color.put("Y23", color(0xfffbe3b3));  sketch_name.put("Y23", "Yellowish Beige");
    sketch_color.put("Y26", color(0xfff0dd67));  sketch_name.put("Y26", "Mustard");
    sketch_color.put("Y28", color(0xffcaa869));  sketch_name.put("Y28", "Lionet Gold");
    sketch_color.put("Y32", color(0xfff9dec0));  sketch_name.put("Y32", "Cashmere");
    sketch_color.put("Y35", color(0xffffd879));  sketch_name.put("Y35", "Maize");
    sketch_color.put("Y38", color(0xffffd374));  sketch_name.put("Y38", "Honey");
    sketch_color.put("YG0000", color(0xfff2f7e0));  sketch_name.put("YG0000", "Lily White");
    sketch_color.put("YG00", color(0xffe6e69e));  sketch_name.put("YG00", "Mimosa Yellow");
    sketch_color.put("YG01", color(0xffe2ebb2));  sketch_name.put("YG01", "Green Bice");
    sketch_color.put("YG03", color(0xffdeeaaa));  sketch_name.put("YG03", "Yellow Green");
    sketch_color.put("YG05", color(0xffd6e592));  sketch_name.put("YG05", "Salad");
    sketch_color.put("YG06", color(0xffc4df92));  sketch_name.put("YG06", "Yellowish Green");
    sketch_color.put("YG07", color(0xffa5cf4f));  sketch_name.put("YG07", "Acid Green");
    sketch_color.put("YG09", color(0xff82c566));  sketch_name.put("YG09", "Lettuce Green");
    sketch_color.put("YG11", color(0xffe5f0d0));  sketch_name.put("YG11", "Mignonette");
    sketch_color.put("YG13", color(0xffd4e59f));  sketch_name.put("YG13", "Chartreuse");
    sketch_color.put("YG17", color(0xff72c156));  sketch_name.put("YG17", "Grass Green");
    sketch_color.put("YG21", color(0xfff7f6be));  sketch_name.put("YG21", "Anise");
    sketch_color.put("YG23", color(0xffe6eb8f));  sketch_name.put("YG23", "New Leaf");
    sketch_color.put("YG25", color(0xffd0e17b));  sketch_name.put("YG25", "Celadon Green");
    sketch_color.put("YG41", color(0xffd5ebd4));  sketch_name.put("YG41", "Pale Cobalt Green");
    sketch_color.put("YG45", color(0xffb4dcb7));  sketch_name.put("YG45", "Cobalt Green");
    sketch_color.put("YG61", color(0xffd6e9d6));  sketch_name.put("YG61", "Pale Moss");
    sketch_color.put("YG63", color(0xffa0caa2));  sketch_name.put("YG63", "Pea Green");
    sketch_color.put("YG67", color(0xff81bf8c));  sketch_name.put("YG67", "Moss");
    sketch_color.put("YG91", color(0xffdad7ae));  sketch_name.put("YG91", "Putty");
    sketch_color.put("YG93", color(0xffd2d29c));  sketch_name.put("YG93", "Grayish Yellow");
    sketch_color.put("YG95", color(0xffcbc65e));  sketch_name.put("YG95", "Pale Olive");
    sketch_color.put("YG97", color(0xff958f03));  sketch_name.put("YG97", "Spanish Olive");
    sketch_color.put("YG99", color(0xff4e6a15));  sketch_name.put("YG99", "Marine Green");
    sketch_color.put("YR0000", color(0xfffff3e5));  sketch_name.put("YR0000", "Pale Chiffon");
    sketch_color.put("YR000", color(0xfffeecd8));  sketch_name.put("YR000", "Silk");
    sketch_color.put("YR00", color(0xfffed6bd));  sketch_name.put("YR00", "Powder Pink");
    sketch_color.put("YR01", color(0xfffedac2));  sketch_name.put("YR01", "Peach Puff");
    sketch_color.put("YR02", color(0xfffcdcc5));  sketch_name.put("YR02", "Light Orange");
    sketch_color.put("YR04", color(0xfffec369));  sketch_name.put("YR04", "Chrome Orange");
    sketch_color.put("YR07", color(0xfff26f39));  sketch_name.put("YR07", "Cadmium Orange");
    sketch_color.put("YR09", color(0xfff15524));  sketch_name.put("YR09", "Chinese Orange");
    sketch_color.put("YR12", color(0xffffe2a6));  sketch_name.put("YR12", "Loquat");
    sketch_color.put("YR14", color(0xfffec84e));  sketch_name.put("YR14", "Caramel");
    sketch_color.put("YR15", color(0xfffbb884));  sketch_name.put("YR15", "Pumpkin Yellow");
    sketch_color.put("YR16", color(0xfffeb729));  sketch_name.put("YR16", "Apricot");
    sketch_color.put("YR18", color(0xfff26b3c));  sketch_name.put("YR18", "Sanguine");
    sketch_color.put("YR20", color(0xffffe1bf));  sketch_name.put("YR20", "Yellowish Shade");
    sketch_color.put("YR21", color(0xfff5ddb1));  sketch_name.put("YR21", "Cream");
    sketch_color.put("YR23", color(0xffeccf8b));  sketch_name.put("YR23", "Yellow Ochre");
    sketch_color.put("YR24", color(0xfff0cf64));  sketch_name.put("YR24", "Pale Sepia");
    sketch_color.put("YR27", color(0xffd56638));  sketch_name.put("YR27", "Tuscan Orange");
    sketch_color.put("YR30", color(0xfffef2da));  sketch_name.put("YR30", "Macadamia Nut");
    sketch_color.put("YR31", color(0xffffdea8));  sketch_name.put("YR31", "Light Reddish Yellow");
    sketch_color.put("YR61", color(0xfffddac4));  sketch_name.put("YR61", "Spring Orange");
    sketch_color.put("YR65", color(0xfffaae60));  sketch_name.put("YR65", "Atoll");
    sketch_color.put("YR68", color(0xfff37022));  sketch_name.put("YR68", "Orange");
    sketch_color.put("YR82", color(0xfffdc68d));  sketch_name.put("YR82", "Mellow Peach");

    original_color.put("0", color(0xffffffff));  original_name.put("0", "Colorless Blender");
    original_color.put("100", color(0xff312b2b));  original_name.put("100", "Black");
    original_color.put("110", color(0xff030708));  original_name.put("110", "Special Black");
    original_color.put("B00", color(0xffddf0f4));  original_name.put("B00", "Frost Blue");
    original_color.put("B01", color(0xffd6eef2));  original_name.put("B01", "Mint Blue");
    original_color.put("B02", color(0xffb3e3f1));  original_name.put("B02", "Robin&#39;s Egg Blue");
    original_color.put("B04", color(0xff73cfe6));  original_name.put("B04", "Tahitian Blue");
    original_color.put("B05", color(0xff40c5e6));  original_name.put("B05", "Process Blue");
    original_color.put("B06", color(0xff00b3e6));  original_name.put("B06", "Peacok Blue");
    original_color.put("B12", color(0xffc8e6f0));  original_name.put("B12", "Ice Blue");
    original_color.put("B14", color(0xff71cfeb));  original_name.put("B14", "Light Blue");
    original_color.put("B16", color(0xff00bcea));  original_name.put("B16", "Cyanine Blue");
    original_color.put("B18", color(0xff1d8acb));  original_name.put("B18", "Lapis Lazuli");
    original_color.put("B21", color(0xffdbedf9));  original_name.put("B21", "Baby Blue");
    original_color.put("B23", color(0xff92c2e8));  original_name.put("B23", "Phthalo Blue");
    original_color.put("B24", color(0xff8acef3));  original_name.put("B24", "Sky");
    original_color.put("B26", color(0xff65b3e3));  original_name.put("B26", "Cobalt Blue");
    original_color.put("B29", color(0xff0177c1));  original_name.put("B29", "Ultramarine");
    original_color.put("B32", color(0xffe2eff7));  original_name.put("B32", "Pale Blue");
    original_color.put("B34", color(0xff82c3ed));  original_name.put("B34", "Manganese Blue");
    original_color.put("B37", color(0xff156fa4));  original_name.put("B37", "Antwerp Blue");
    original_color.put("B39", color(0xff2b64a9));  original_name.put("B39", "Prussian Blue");
    original_color.put("B41", color(0xffe2f0fb));  original_name.put("B41", "Powder Blue");
    original_color.put("B45", color(0xff75c0ea));  original_name.put("B45", "Smoky Blue");
    original_color.put("BG02", color(0xffc6e8ea));  original_name.put("BG02", "New Blue");
    original_color.put("BG05", color(0xff83d2e1));  original_name.put("BG05", "Holiday Blue");
    original_color.put("BG09", color(0xff01b1c9));  original_name.put("BG09", "Blue Green");
    original_color.put("BG10", color(0xffdcf0ef));  original_name.put("BG10", "Cool Shadow");
    original_color.put("BG11", color(0xffceebf1));  original_name.put("BG11", "Moon White");
    original_color.put("BG13", color(0xffc4e7e9));  original_name.put("BG13", "Mint Green");
    original_color.put("BG15", color(0xffa0d9d2));  original_name.put("BG15", "Aqua");
    original_color.put("BG18", color(0xff37c0b0));  original_name.put("BG18", "Teal Blue");
    original_color.put("BG32", color(0xffbce2d7));  original_name.put("BG32", "Aqua Mint");
    original_color.put("BG34", color(0xffa3dad7));  original_name.put("BG34", "Horizon Green");
    original_color.put("BG45", color(0xffafdfdf));  original_name.put("BG45", "Nile Blue");
    original_color.put("BG49", color(0xff00b6b9));  original_name.put("BG49", "Duck Blue");
    original_color.put("BG99", color(0xff6e9b87));  original_name.put("BG99", "Flagstone Blue");
    original_color.put("BV00", color(0xffe0dced));  original_name.put("BV00", "Mauve Shadow");
    original_color.put("BV04", color(0xff7c97ce));  original_name.put("BV04", "Blue Berry");
    original_color.put("BV08", color(0xff9d7eb9));  original_name.put("BV08", "Blue Violet");
    original_color.put("BV23", color(0xffb1c0dd));  original_name.put("BV23", "Grayish Lavender");
    original_color.put("BV31", color(0xffeae7f2));  original_name.put("BV31", "Pale Lavender");
    original_color.put("C0", color(0xffe0e7ed));  original_name.put("C0", "Cool Gray");
    original_color.put("C10", color(0xff202b31));  original_name.put("C10", "Cool Gray");
    original_color.put("C1", color(0xffdae3e8));  original_name.put("C1", "Cool Gray No. 1");
    original_color.put("C2", color(0xffccd7dd));  original_name.put("C2", "Cool Gray");
    original_color.put("C3", color(0xffc1ccd2));  original_name.put("C3", "Cool Gray No. 3");
    original_color.put("C4", color(0xffa6b4bd));  original_name.put("C4", "Cool Gray");
    original_color.put("C5", color(0xff92a0ab));  original_name.put("C5", "Cool Gray No. 5");
    original_color.put("C6", color(0xff7b8c96));  original_name.put("C6", "Cool Gray");
    original_color.put("C7", color(0xff637079));  original_name.put("C7", "Cool Gray No. 7");
    original_color.put("C8", color(0xff535d66));  original_name.put("C8", "Cool Gray");
    original_color.put("C9", color(0xff3c474d));  original_name.put("C9", "Cool Gray");
    original_color.put("E00", color(0xfffdf3ea));  original_name.put("E00", "Cotton Pearl");
    original_color.put("E02", color(0xfffeece0));  original_name.put("E02", "Fruit Pink");
    original_color.put("E04", color(0xffe4bcc4));  original_name.put("E04", "Lipstick Natural");
    original_color.put("E07", color(0xffcc816a));  original_name.put("E07", "Light Mahogany");
    original_color.put("E09", color(0xffd96a4f));  original_name.put("E09", "Burnt Sienna");
    original_color.put("E11", color(0xfffee9d6));  original_name.put("E11", "Barley Beige");
    original_color.put("E13", color(0xffe9c5af));  original_name.put("E13", "Light Suntan");
    original_color.put("E15", color(0xfffbbb8d));  original_name.put("E15", "Dark Suntan");
    original_color.put("E19", color(0xffc45238));  original_name.put("E19", "Redwood");
    original_color.put("E21", color(0xfffde2c7));  original_name.put("E21", "Soft Sun");
    original_color.put("E25", color(0xffd2a482));  original_name.put("E25", "Caribe Cocoa");
    original_color.put("E27", color(0xff997663));  original_name.put("E27", "Milk Chocolate");
    original_color.put("E29", color(0xff884636));  original_name.put("E29", "Burnt Umber");
    original_color.put("E31", color(0xfff2e6ce));  original_name.put("E31", "Brick Beige");
    original_color.put("E33", color(0xfff3d2b1));  original_name.put("E33", "Sand");
    original_color.put("E34", color(0xfff0caa6));  original_name.put("E34", "Toast");
    original_color.put("E35", color(0xffe6c3a3));  original_name.put("E35", "Chamois");
    original_color.put("E37", color(0xffcc9159));  original_name.put("E37", "Sepia");
    original_color.put("E39", color(0xffc5743f));  original_name.put("E39", "Leather");
    original_color.put("E40", color(0xfff2e8dc));  original_name.put("E40", "Brick White");
    original_color.put("E41", color(0xfffef1e1));  original_name.put("E41", "Pearl White");
    original_color.put("E43", color(0xffe8dabd));  original_name.put("E43", "Dull Ivory");
    original_color.put("E44", color(0xffc5b9a9));  original_name.put("E44", "Clay");
    original_color.put("E49", color(0xff634c3c));  original_name.put("E49", "Dark Bark");
    original_color.put("E51", color(0xfffeecd6));  original_name.put("E51", "Milky White");
    original_color.put("E53", color(0xfff3e6c3));  original_name.put("E53", "Raw Silk");
    original_color.put("E55", color(0xfff1dfb9));  original_name.put("E55", "Light Camel");
    original_color.put("E57", color(0xffb18558));  original_name.put("E57", "Light Walnut");
    original_color.put("E59", color(0xff9a7f6c));  original_name.put("E59", "Walnut");
    original_color.put("E77", color(0xff7f604e));  original_name.put("E77", "Maroon");
    original_color.put("G00", color(0xffe3f2ed));  original_name.put("G00", "Jade Green");
    original_color.put("G02", color(0xffcfe8d3));  original_name.put("G02", "Spectrum Green");
    original_color.put("G05", color(0xff69c07b));  original_name.put("G05", "Emerald Green");
    original_color.put("G07", color(0xff7bc576));  original_name.put("G07", "Nile Green");
    original_color.put("G09", color(0xff7ac465));  original_name.put("G09", "Veronese Green");
    original_color.put("G12", color(0xffd2e8c4));  original_name.put("G12", "Sea Green");
    original_color.put("G14", color(0xff97cf90));  original_name.put("G14", "Apple Green");
    original_color.put("G16", color(0xff60c198));  original_name.put("G16", "Malachite");
    original_color.put("G17", color(0xff14b37d));  original_name.put("G17", "Forest Green");
    original_color.put("G19", color(0xff2db98a));  original_name.put("G19", "Bright Parrot Green");
    original_color.put("G20", color(0xffedf6db));  original_name.put("G20", "Wax White");
    original_color.put("G21", color(0xffc4e4cd));  original_name.put("G21", "Lime Green");
    original_color.put("G24", color(0xffc3e0b4));  original_name.put("G24", "Willow");
    original_color.put("G28", color(0xff119462));  original_name.put("G28", "Ocean Green");
    original_color.put("G29", color(0xff197c5d));  original_name.put("G29", "Pine Tree Green");
    original_color.put("G40", color(0xffe4f1df));  original_name.put("G40", "Dim Green");
    original_color.put("G82", color(0xffccdab9));  original_name.put("G82", "Spring Dim Green");
    original_color.put("G85", color(0xff9dc3aa));  original_name.put("G85", "Verdigris");
    original_color.put("G99", color(0xff5f7e3a));  original_name.put("G99", "Olive");
    original_color.put("N0", color(0xffeceeed));  original_name.put("N0", "Neutral Gray");
    original_color.put("N10", color(0xff312f30));  original_name.put("N10", "Neutral Gray");
    original_color.put("N1", color(0xffe2e3e5));  original_name.put("N1", "Neutral Gray");
    original_color.put("N2", color(0xffdadbdd));  original_name.put("N2", "Neutral Gray");
    original_color.put("N3", color(0xffd1d2d4));  original_name.put("N3", "Neutral Gray");
    original_color.put("N4", color(0xffbcbdc1));  original_name.put("N4", "Neutral Gray");
    original_color.put("N5", color(0xffa8a9ad));  original_name.put("N5", "Neutral Gray");
    original_color.put("N6", color(0xff949599));  original_name.put("N6", "Neutral Gray");
    original_color.put("N7", color(0xff77787c));  original_name.put("N7", "Neutral Gray");
    original_color.put("N8", color(0xff636466));  original_name.put("N8", "Neutral Gray");
    original_color.put("N9", color(0xff4c4d4f));  original_name.put("N9", "Neutral Gray");
    original_color.put("R00", color(0xfffeeae1));  original_name.put("R00", "Pinkish White");
    original_color.put("R02", color(0xfffdd3c7));  original_name.put("R02", "Rose Salmon");
    original_color.put("R05", color(0xfff6927b));  original_name.put("R05", "Salmon Red");
    original_color.put("R08", color(0xfff26754));  original_name.put("R08", "Vermilion");
    original_color.put("R11", color(0xfffde1d5));  original_name.put("R11", "Pale Cherry Pink");
    original_color.put("R17", color(0xfff4846c));  original_name.put("R17", "Lipstick Orange");
    original_color.put("R20", color(0xfffcd7cf));  original_name.put("R20", "Blush");
    original_color.put("R24", color(0xfff27579));  original_name.put("R24", "Prawn");
    original_color.put("R27", color(0xfff15062));  original_name.put("R27", "Cadmium Red");
    original_color.put("R29", color(0xffed174b));  original_name.put("R29", "Lipstick Red");
    original_color.put("R32", color(0xfffac1ba));  original_name.put("R32", "Peach");
    original_color.put("R35", color(0xfff27185));  original_name.put("R35", "Coral");
    original_color.put("R37", color(0xffe86c74));  original_name.put("R37", "Carmine");
    original_color.put("R39", color(0xffcb487a));  original_name.put("R39", "Garnet");
    original_color.put("R59", color(0xffb74f70));  original_name.put("R59", "Cardinal");
    original_color.put("RV02", color(0xfffad5e6));  original_name.put("RV02", "Sugared Almond Pink");
    original_color.put("RV04", color(0xfff6a3bf));  original_name.put("RV04", "Shock  Pink");
    original_color.put("RV06", color(0xfff386af));  original_name.put("RV06", "Cerise");
    original_color.put("RV09", color(0xffe171ac));  original_name.put("RV09", "Fuchsia");
    original_color.put("RV10", color(0xfffdecf4));  original_name.put("RV10", "Pale Pink");
    original_color.put("RV11", color(0xfffbd6dd));  original_name.put("RV11", "Pink");
    original_color.put("RV13", color(0xfff9c9d7));  original_name.put("RV13", "Tender Pink");
    original_color.put("RV14", color(0xfff495b7));  original_name.put("RV14", "Begonia Pink");
    original_color.put("RV17", color(0xffdb7eb3));  original_name.put("RV17", "Deep Magenta");
    original_color.put("RV19", color(0xffd268aa));  original_name.put("RV19", "Red Violet");
    original_color.put("RV21", color(0xfffde8e7));  original_name.put("RV21", "Light Pink");
    original_color.put("RV25", color(0xfff493be));  original_name.put("RV25", "Dog Rose Flower");
    original_color.put("RV29", color(0xffef4880));  original_name.put("RV29", "Crimson");
    original_color.put("RV32", color(0xfffad3ce));  original_name.put("RV32", "Shadow Pink");
    original_color.put("RV34", color(0xfff9afae));  original_name.put("RV34", "Dark Pink");
    original_color.put("T0", color(0xffeceeed));  original_name.put("T0", "Toner Gray");
    original_color.put("T10", color(0xff322e2d));  original_name.put("T10", "Toner Gray");
    original_color.put("T1", color(0xffeaeae8));  original_name.put("T1", "Toner Gray");
    original_color.put("T2", color(0xffe0e0de));  original_name.put("T2", "Toner Gray");
    original_color.put("T3", color(0xffd1d2cc));  original_name.put("T3", "Toner Gray");
    original_color.put("T4", color(0xffbcbbb9));  original_name.put("T4", "Toner Gray");
    original_color.put("T5", color(0xffa8a7a3));  original_name.put("T5", "Toner Gray");
    original_color.put("T6", color(0xff949590));  original_name.put("T6", "Toner Gray");
    original_color.put("T7", color(0xff777674));  original_name.put("T7", "Toner Gray");
    original_color.put("T8", color(0xff63645f));  original_name.put("T8", "Toner Gray");
    original_color.put("T9", color(0xff4c4b49));  original_name.put("T9", "Toner Gray");
    original_color.put("V04", color(0xffe6aace));  original_name.put("V04", "Lilac");
    original_color.put("V06", color(0xffce95c2));  original_name.put("V06", "Lavender");
    original_color.put("V09", color(0xff8754a1));  original_name.put("V09", "Violet");
    original_color.put("V12", color(0xffeed7e9));  original_name.put("V12", "Pale Lilac");
    original_color.put("V15", color(0xffd3a6cd));  original_name.put("V15", "Mallow");
    original_color.put("V17", color(0xffa092c7));  original_name.put("V17", "Amethyst");
    original_color.put("W0", color(0xffecece4));  original_name.put("W0", "Warm Gray");
    original_color.put("W10", color(0xff302f2b));  original_name.put("W10", "Warm Gray");
    original_color.put("W1", color(0xffe7e7df));  original_name.put("W1", "Warm Gray No. 1");
    original_color.put("W2", color(0xffddddd5));  original_name.put("W2", "Warm Gray");
    original_color.put("W3", color(0xffd2d2ca));  original_name.put("W3", "Warm Gray No. 3");
    original_color.put("W4", color(0xffbcbdb7));  original_name.put("W4", "Warm Gray");
    original_color.put("W5", color(0xffa8a9a4));  original_name.put("W5", "Warm Gray No. 5");
    original_color.put("W6", color(0xff94958f));  original_name.put("W6", "Warm Gray");
    original_color.put("W7", color(0xff777873));  original_name.put("W7", "Warm Gray No. 7");
    original_color.put("W8", color(0xff63645f));  original_name.put("W8", "Warm Gray");
    original_color.put("W9", color(0xff4c4d48));  original_name.put("W9", "Warm Gray");
    original_color.put("Y00", color(0xfffefddf));  original_name.put("Y00", "Barium Yellow");
    original_color.put("Y02", color(0xfff6f396));  original_name.put("Y02", "Canary Yellow");
    original_color.put("Y06", color(0xfffef56c));  original_name.put("Y06", "Yellow");
    original_color.put("Y08", color(0xfffef200));  original_name.put("Y08", "Acid Yellow");
    original_color.put("Y11", color(0xfffffbcc));  original_name.put("Y11", "Pale Yellow");
    original_color.put("Y13", color(0xfffbf7ae));  original_name.put("Y13", "Lemon Yellow");
    original_color.put("Y15", color(0xfffee96c));  original_name.put("Y15", "Cadmium Yellow");
    original_color.put("Y17", color(0xffffe455));  original_name.put("Y17", "Golden Yellow");
    original_color.put("Y19", color(0xffffe93e));  original_name.put("Y19", "Napoli Yellow");
    original_color.put("Y21", color(0xffffeec2));  original_name.put("Y21", "Buttercup Yellow");
    original_color.put("Y23", color(0xfffbe3b3));  original_name.put("Y23", "Yellowish Beige");
    original_color.put("Y26", color(0xfff0dd67));  original_name.put("Y26", "Mustard");
    original_color.put("Y38", color(0xffffd374));  original_name.put("Y38", "Honey");
    original_color.put("YG01", color(0xffe2ebb2));  original_name.put("YG01", "Green Bice");
    original_color.put("YG03", color(0xffdeeaaa));  original_name.put("YG03", "Yellow Green");
    original_color.put("YG05", color(0xffd6e592));  original_name.put("YG05", "Salad");
    original_color.put("YG07", color(0xffa5cf4f));  original_name.put("YG07", "Acid Green");
    original_color.put("YG09", color(0xff82c566));  original_name.put("YG09", "Lettuce Green");
    original_color.put("YG11", color(0xffe5f0d0));  original_name.put("YG11", "Mignonette");
    original_color.put("YG13", color(0xffd4e59f));  original_name.put("YG13", "Chartreuse");
    original_color.put("YG17", color(0xff72c156));  original_name.put("YG17", "Grass Green");
    original_color.put("YG21", color(0xfff7f6be));  original_name.put("YG21", "Anise");
    original_color.put("YG23", color(0xffe6eb8f));  original_name.put("YG23", "New Leaf");
    original_color.put("YG25", color(0xffd0e17b));  original_name.put("YG25", "Celadon Green");
    original_color.put("YG41", color(0xffd5ebd4));  original_name.put("YG41", "Pale Cobalt Green");
    original_color.put("YG45", color(0xffb4dcb7));  original_name.put("YG45", "Cobalt Green");
    original_color.put("YG63", color(0xffa0caa2));  original_name.put("YG63", "Pea Green");
    original_color.put("YG67", color(0xff81bf8c));  original_name.put("YG67", "Moss");
    original_color.put("YG91", color(0xffdad7ae));  original_name.put("YG91", "Putty");
    original_color.put("YG95", color(0xffcbc65e));  original_name.put("YG95", "Pale Olive");
    original_color.put("YG97", color(0xff958f03));  original_name.put("YG97", "Spanish Olive");
    original_color.put("YG99", color(0xff4e6a15));  original_name.put("YG99", "Marine Green");
    original_color.put("YR00", color(0xfffed6bd));  original_name.put("YR00", "Powder Pink");
    original_color.put("YR02", color(0xfffcdcc5));  original_name.put("YR02", "Light Orange");
    original_color.put("YR04", color(0xfffec369));  original_name.put("YR04", "Chrome Orange");
    original_color.put("YR07", color(0xfff26f39));  original_name.put("YR07", "Cadmium Orange");
    original_color.put("YR09", color(0xfff15524));  original_name.put("YR09", "Chinese Orange");
    original_color.put("YR14", color(0xfffec84e));  original_name.put("YR14", "Caramel");
    original_color.put("YR16", color(0xfffeb729));  original_name.put("YR16", "Apricot");
    original_color.put("YR18", color(0xfff26b3c));  original_name.put("YR18", "Sanguine");
    original_color.put("YR21", color(0xfff5ddb1));  original_name.put("YR21", "Cream");
    original_color.put("YR23", color(0xffeccf8b));  original_name.put("YR23", "Yellow Ochre");
    original_color.put("YR24", color(0xfff0cf64));  original_name.put("YR24", "Pale Sepia");
  }
  
  public int get_sketch_color(String pen) {
    return sketch_color.get(pen);
  }

  public int get_original_color(String pen) {
    return original_color.get(pen);
  }

  public String get_sketch_name(String pen) {
    return sketch_name.get(pen);
  }

  public String get_original_name(String pen) {
    return original_name.get(pen);
  }

  public String get_closest_original(int c1) {
    //http://stackoverflow.com/questions/1847092/given-an-rgb-value-what-would-be-the-best-way-to-find-the-closest-match-in-the-d
    //https://en.wikipedia.org/wiki/Color_difference
    
    float r1 = red(c1);
    float g1 = green(c1);
    float b1 = blue(c1);
    
    float closest_value = 99999999999999999999999999.0f;
    String closest_pen = "";
    
    for (Map.Entry me : original_color.entrySet()) {
      //println(me.getKey() + " is " + me.getValue());
    
      int c2 = (int)me.getValue();
      float r2 = red(c2);
      float g2 = green(c2);
      float b2 = blue(c2);
    
      float d = sq((r2-r1)*0.30f) + sq((g2-g1)*0.59f) + sq((b2-b1)*0.11f);
      if (d<closest_value) {
        closest_value = d;
        closest_pen = (String)me.getKey();
      }
    }
    return closest_pen;
  }
  
  public String get_closest_sketch(int c1) {
    //http://stackoverflow.com/questions/1847092/given-an-rgb-value-what-would-be-the-best-way-to-find-the-closest-match-in-the-d
    //https://en.wikipedia.org/wiki/Color_difference
    
    float r1 = red(c1);
    float g1 = green(c1);
    float b1 = blue(c1);
    
    float closest_value = 99999999999999999999999999.0f;
    String closest_pen = "";
    
    for (Map.Entry me : sketch_color.entrySet()) {
      //println(me.getKey() + " is " + me.getValue());
    
      int c2 = (int)me.getValue();
      float r2 = red(c2);
      float g2 = green(c2);
      float b2 = blue(c2);
    
      float d = sq(abs((r2-r1))*0.30f) + sq(abs((g2-g1))*0.59f) + sq(abs((b2-b1))*0.11f);
      //float d = sq((r2-r1)*0.30) + sq((g2-g1)*0.59) + sq((b2-b1)*0.11);
      if (d<closest_value) {
        closest_value = d;
        closest_pen = (String)me.getKey();
      }
    }
    return closest_pen;
  }
  
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////
// No, it's not a fancy dancy class like the snot nosed kids are doing these days.
// Now get the hell off my lawn.

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void gcode_header() {
  OUTPUT.println("G21");
  OUTPUT.println("G90");
  OUTPUT.println("G1 Z0");
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void gcode_trailer() {
  OUTPUT.println("G1 Z0");
  OUTPUT.println("G1 X" + gcode_format(0.1f) + " Y" + gcode_format(0.1f));
  OUTPUT.println("G1 X0 y0");
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void gcode_comment(String comment) {
  gcode_comments += ("(" + comment + ")") + "\n";
  println(comment);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void pen_up() {
  is_pen_down = false;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void pen_down() {
  is_pen_down = true;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void move_abs(int pen_number, float x, float y) {
  
  d1.addline(pen_number, is_pen_down, old_x, old_y, x, y);
  if (is_pen_down) {
    d1.render_last();
  }
  
  old_x = x;
  old_y = y;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public String gcode_format (Float n) {
  String s = nf(n, 0, gcode_decimals);
  s = s.replace('.', gcode_decimal_seperator);
  s = s.replace(',', gcode_decimal_seperator);
  return s; 
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void create_gcode_files (int line_count) {
  boolean is_pen_down;
  int pen_lifts;
  float pen_movement;
  float pen_drawing;
  int   lines_drawn;
  float x;
  float y;
  float distance;
  
  // Loop over all lines for every pen.
  for (int p=0; p<pen_count; p++) {    
    is_pen_down = false;
    pen_lifts = 2;
    pen_movement = 0;
    pen_drawing = 0;
    lines_drawn = 0;
    x = 0;
    y = 0;
    String gname = "gcode\\gcode_" + basefile_selected + "_pen" + p + "_" + copic_sets[current_copic_set][p] + ".txt";
    OUTPUT = createWriter(sketchPath("") + gname);
    OUTPUT.println(gcode_comments);
    gcode_header();
    
    for (int i=1; i<line_count; i++) { 
      if (d1.lines[i].pen_number == p) {
        
        float gcode_scaled_x1 = d1.lines[i].x1 * gcode_scale + gcode_offset_x;
        float gcode_scaled_y1 = d1.lines[i].y1 * gcode_scale + gcode_offset_y;
        float gcode_scaled_x2 = d1.lines[i].x2 * gcode_scale + gcode_offset_x;
        float gcode_scaled_y2 = d1.lines[i].y2 * gcode_scale + gcode_offset_y;
        distance = sqrt( sq(abs(gcode_scaled_x1 - gcode_scaled_x2)) + sq(abs(gcode_scaled_y1 - gcode_scaled_y2)) );
 
        if (x != gcode_scaled_x1 || y != gcode_scaled_y1) {
          // Oh crap, where the line starts is not where I am, pick up the pen and move there.
          OUTPUT.println("G1 Z0");
          is_pen_down = false;
          distance = sqrt( sq(abs(x - gcode_scaled_x1)) + sq(abs(y - gcode_scaled_y1)) );
          String buf = "G1 X" + gcode_format(gcode_scaled_x1) + " Y" + gcode_format(gcode_scaled_y1);
          OUTPUT.println(buf);
          x = gcode_scaled_x1;
          y = gcode_scaled_y1;
          pen_movement = pen_movement + distance;
          pen_lifts++;
        }
        
        if (d1.lines[i].pen_down) {
          if (is_pen_down == false) {
            OUTPUT.println("G1 Z1");
            is_pen_down = true;
          }
          pen_drawing = pen_drawing + distance;
          lines_drawn++;
        } else {
          if (is_pen_down == true) {
            OUTPUT.println("G1 Z0");
            is_pen_down = false;
            pen_movement = pen_movement + distance;
            pen_lifts++;
          }
        }
        
        String buf = "G1 X" + gcode_format(gcode_scaled_x2) + " Y" + gcode_format(gcode_scaled_y2);
        OUTPUT.println(buf);
        x = gcode_scaled_x2;
        y = gcode_scaled_y2;
        dx.update_limit(gcode_scaled_x2);
        dy.update_limit(gcode_scaled_y2);
      }
    }
    
    gcode_trailer();
    OUTPUT.println("(Drew " + lines_drawn + " lines for " + pen_drawing  / 25.4f / 12 + " feet)");
    OUTPUT.println("(Pen was lifted " + pen_lifts + " times for " + pen_movement  / 25.4f / 12 + " feet)");
    OUTPUT.println("(Extreams of X: " + dx.min + " thru " + dx.max + ")");
    OUTPUT.println("(Extreams of Y: " + dy.min + " thru " + dy.max + ")");
    OUTPUT.flush();
    OUTPUT.close();
    println("gcode created:  " + gname);
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void create_gcode_test_file () {
  // The dx.min are already scaled to gcode.
  float test_length = 25.4f * 2;
  
  String gname = "gcode\\gcode_" + basefile_selected + "_test.txt";
  OUTPUT = createWriter(sketchPath("") + gname);
  OUTPUT.println("(This is a test file to draw the extreams of the drawing area.)");
  OUTPUT.println("(Draws a 2 inch mark on all four corners of the paper.)");
  OUTPUT.println("(WARNING:  pen will be down.)");
  OUTPUT.println("(Extreams of X: " + dx.min + " thru " + dx.max + ")");
  OUTPUT.println("(Extreams of Y: " + dy.min + " thru " + dy.max + ")");
  gcode_header();
  
  OUTPUT.println("(Upper left)");
  OUTPUT.println("G1 X" + gcode_format(dx.min) + " Y" + gcode_format(dy.min + test_length));
  OUTPUT.println("G1 Z1");
  OUTPUT.println("G1 X" + gcode_format(dx.min) + " Y" + gcode_format(dy.min));
  OUTPUT.println("G1 X" + gcode_format(dx.min + test_length) + " Y" + gcode_format(dy.min));
  OUTPUT.println("G1 Z0");

  OUTPUT.println("(Upper right)");
  OUTPUT.println("G1 X" + gcode_format(dx.max - test_length) + " Y" + gcode_format(dy.min));
  OUTPUT.println("G1 Z1");
  OUTPUT.println("G1 X" + gcode_format(dx.max) + " Y" + gcode_format(dy.min));
  OUTPUT.println("G1 X" + gcode_format(dx.max) + " Y" + gcode_format(dy.min + test_length));
  OUTPUT.println("G1 Z0");

  OUTPUT.println("(Lower right)");
  OUTPUT.println("G1 X" + gcode_format(dx.max) + " Y" + gcode_format(dy.max - test_length));
  OUTPUT.println("G1 Z1");
  OUTPUT.println("G1 X" + gcode_format(dx.max) + " Y" + gcode_format(dy.max));
  OUTPUT.println("G1 X" + gcode_format(dx.max - test_length) + " Y" + gcode_format(dy.max));
  OUTPUT.println("G1 Z0");

  OUTPUT.println("(Lower left)");
  OUTPUT.println("G1 X" + gcode_format(dx.min + test_length) + " Y" + gcode_format(dy.max));
  OUTPUT.println("G1 Z1");
  OUTPUT.println("G1 X" + gcode_format(dx.min) + " Y" + gcode_format(dy.max));
  OUTPUT.println("G1 X" + gcode_format(dx.min) + " Y" + gcode_format(dy.max - test_length));
  OUTPUT.println("G1 Z0");

  gcode_trailer();
  OUTPUT.flush();
  OUTPUT.close();
  println("gcode test created:  " + gname);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
// Thanks to Vladimir Bochkov for helping me debug the SVG international decimal separators problem.
public String svg_format (Float n) {
  final char regional_decimal_separator = ',';
  final char svg_decimal_seperator = '.';
  
  String s = nf(n, 0, svg_decimals);
  s = s.replace(regional_decimal_separator, svg_decimal_seperator);
  return s;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
// Thanks to John Cliff for getting the SVG output moving forward.
public void create_svg_file (int line_count) {
  boolean drawing_polyline = false;
  
  // Inkscape versions before 0.91 used 90dpi, Today most software assumes 96dpi.
  float svgdpi = 96.0f / 25.4f;
  
  String gname = "gcode\\gcode_" + basefile_selected + ".svg";
  OUTPUT = createWriter(sketchPath("") + gname);
  OUTPUT.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
  OUTPUT.println("<svg width=\"" + svg_format(img.width * gcode_scale) + "mm\" height=\"" + svg_format(img.height * gcode_scale) + "mm\" xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\">");
  d1.set_pen_continuation_flags();
  
  // Loop over pens backwards to display dark lines last.
  // Then loop over all displayed lines.
  for (int p=pen_count-1; p>=0; p--) {    
    OUTPUT.println("<g id=\"" + copic_sets[current_copic_set][p] + "\">");
    for (int i=1; i<line_count; i++) { 
      if (d1.lines[i].pen_number == p) {

        // Do we add gcode_offsets needed by my bot, or zero based?
        //float gcode_scaled_x1 = d1.lines[i].x1 * gcode_scale * svgdpi + gcode_offset_x;
        //float gcode_scaled_y1 = d1.lines[i].y1 * gcode_scale * svgdpi + gcode_offset_y;
        //float gcode_scaled_x2 = d1.lines[i].x2 * gcode_scale * svgdpi + gcode_offset_x;
        //float gcode_scaled_y2 = d1.lines[i].y2 * gcode_scale * svgdpi + gcode_offset_y;
        
        float gcode_scaled_x1 = d1.lines[i].x1 * gcode_scale * svgdpi;
        float gcode_scaled_y1 = d1.lines[i].y1 * gcode_scale * svgdpi;
        float gcode_scaled_x2 = d1.lines[i].x2 * gcode_scale * svgdpi;
        float gcode_scaled_y2 = d1.lines[i].y2 * gcode_scale * svgdpi;

        if (d1.lines[i].pen_continuation == false && drawing_polyline) {
          OUTPUT.println("\" />");
          drawing_polyline = false;
        }

        if (d1.lines[i].pen_down) {
          if (d1.lines[i].pen_continuation) {
            String buf = svg_format(gcode_scaled_x2) + "," + svg_format(gcode_scaled_y2);
            OUTPUT.println(buf);
            drawing_polyline = true;
          } else {
            int c = copic.get_original_color(copic_sets[current_copic_set][p]);
            OUTPUT.println("<polyline fill=\"none\" stroke=\"#" + hex(c, 6) + "\" stroke-width=\"1.0\" stroke-opacity=\"1\" points=\"");
            String buf = svg_format(gcode_scaled_x1) + "," + svg_format(gcode_scaled_y1);
            OUTPUT.println(buf);
            drawing_polyline = true;
          }
        }
      }
    }
    if (drawing_polyline) {
      OUTPUT.println("\" />");
      drawing_polyline = false;
    }
    OUTPUT.println("</g>");
  }
  OUTPUT.println("</svg>");
  OUTPUT.flush();
  OUTPUT.close();
  println("SVG created:  " + gname);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_threshold() {
  gcode_comment("Thresholed");
  img.filter(THRESHOLD);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_desaturate() {
  gcode_comment("image_desaturate");
  img.filter(GRAY);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_invert() {
  gcode_comment("image_invert");
  img.filter(INVERT);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_posterize(int amount) {
  gcode_comment("image_posterize");
  img.filter(POSTERIZE, amount);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_blur(int amount) {
  gcode_comment("image_blur");
  img.filter(BLUR, amount);
}
 
///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_erode() {
  gcode_comment("image_erode");
  img.filter(ERODE);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_dilate() {
  gcode_comment("image_dilate");
  img.filter(DILATE);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
public void save_jpg() {
  // Currently disabled.
  // Must not be called from event handling functions such as keyPressed()
  PImage  img_drawing;
  PImage  img_drawing2;

  //img_drawing = createImage(img.width, img.height, RGB);
  //img_drawing.copy(0, 0, img.width, img.height, 0, 0, img.width, img.height);
  //img_drawing.save("what the duce.jpg");

  // Save resuling image
  save("tmptif.tif");
  img_drawing = loadImage("tmptif.tif");
  img_drawing2 = createImage(img.width, img.height, RGB);
  img_drawing2.copy(img_drawing, 0, 0, img.width, img.height, 0, 0, img.width, img.height);
  img_drawing2.save("gcode\\gcode_" + basefile_selected + ".jpg");
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_rotate() {
  //image[y][x]                                     // assuming this is the original orientation
  //image[x][original_width - y]                    // rotated 90 degrees ccw
  //image[original_height - x][y]                   // 90 degrees cw
  //image[original_height - y][original_width - x]  // 180 degrees

  if (img.width > img.height) {
    PImage img2 = createImage(img.height, img.width, RGB);
    img.loadPixels();
    for (int x=1; x<img.width; x++) {
      for (int y=1; y<img.height; y++) {
        int loc1 = x + y*img.width;
        int loc2 = y + (img.width - x) * img2.width;
        img2.pixels[loc2] = img.pixels[loc1];
      }
    }
    img = img2;
    updatePixels();
    gcode_comment("image_rotate: rotated 90 degrees to fit machines sweet spot");
  } else {
    gcode_comment("image_rotate: no rotation necessary");
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void lighten_one_pixel(int adjustbrightness, int x, int y) {
  int loc = (y)*img.width + x;
  float r = brightness (img.pixels[loc]);
  //r += adjustbrightness;
  r += adjustbrightness + random(0, 0.01f);
  r = constrain(r,0,255);
  int c = color(r);
  img.pixels[loc] = c;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_scale(int new_width) {
  if (img.width != new_width) {
    gcode_comment("image_scale, old size: " + img.width + " by " + img.height + "     ratio: " + (float)img.width / (float)img.height);
    img.resize(new_width, 0);
    gcode_comment("image_scale, new size: " + img.width + " by " + img.height + "     ratio: " + (float)img.width / (float)img.height);
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public float avg_imgage_brightness() {
  float b = 0.0f;

  for (int p=0; p < img.width * img.height; p++) {
    b += brightness(img.pixels[p]);
  }
  
  return(b / (img.width * img.height));
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_crop() {
  // This will center crop to the desired image size image_size_x and image_size_y
  
  PImage img2;
  float desired_ratio = image_size_x / image_size_y;
  float current_ratio = (float)img.width / (float)img.height;
  
  gcode_comment("image_crop desired ratio of " + desired_ratio);
  gcode_comment("image_crop old size: " + img.width + " by " + img.height + "     ratio: " + current_ratio);
  
  if (current_ratio < desired_ratio) {
    int desired_x = img.width;
    int desired_y = PApplet.parseInt(img.width / desired_ratio);
    int half_y = (img.height - desired_y) / 2;
    img2 = createImage(desired_x, desired_y, RGB);
    img2.copy(img, 0, half_y, desired_x, desired_y, 0, 0, desired_x, desired_y);
  } else {
    int desired_x = PApplet.parseInt(img.height * desired_ratio);
    int desired_y = img.height;
    int half_x = (img.width - desired_x) / 2;
    img2 = createImage(desired_x, desired_y, RGB);
    img2.copy(img, half_x, 0, desired_x, desired_y, 0, 0, desired_x, desired_y);
  }

  img = img2;
  gcode_comment("image_crop new size: " + img.width + " by " + img.height + "     ratio: " + (float)img.width / (float)img.height);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_boarder(String fname, int shrink, int blur) {
  // A quick and dirty way of softening the edges of your drawing.
  // Look in the boarders directory for some examples.
  // Ideally, the boarder will have similar dimensions as the image to be drawn.
  // For far more control, just edit your input image directly.
  // Most of the examples are pretty heavy handed so you can "shrink" them a few pixels as desired.
  // It does not matter if you use a transparant background or just white.  JPEG or PNG, it's all good.
  //
  // fname:   Name of boarder file.
  // shrink:  Number of pixels to pull the boarder away, 0 for no change. 
  // blur:    Guassian blur the boarder, 0 for no blur, 10+ for a lot.
  
  //PImage boarder = createImage(img.width+(shrink*2), img.height+(shrink*2), RGB);
  PImage temp_boarder = loadImage("boarder/" + fname);
  temp_boarder.resize(img.width, img.height);
  temp_boarder.filter(GRAY);
  temp_boarder.filter(INVERT);
  temp_boarder.filter(BLUR, blur);
  
  //boarder.copy(temp_boarder, 0, 0, temp_boarder.width, temp_boarder.height, 0, 0, boarder.width, boarder.height);
  img.blend(temp_boarder, shrink, shrink, img.width, img.height,  0, 0, img.width, img.height, ADD); 
  gcode_comment("image_boarder: " + fname + "   " + shrink + "   " + blur);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_unsharpen(PImage img, int amount) {
  // Source:  https://www.taylorpetrick.com/blog/post/convolution-part3
  // Subtle unsharp matrix
  float[][] matrix = { { -0.00391f, -0.01563f, -0.02344f, -0.01563f, -0.00391f },
                       { -0.01563f, -0.06250f, -0.09375f, -0.06250f, -0.01563f },
                       { -0.02344f, -0.09375f,  1.85980f, -0.09375f, -0.02344f },
                       { -0.01563f, -0.06250f, -0.09375f, -0.06250f, -0.01563f },
                       { -0.00391f, -0.01563f, -0.02344f, -0.01563f, -0.00391f } };
  
  
  //print_matrix(matrix);
  matrix = scale_matrix(matrix, amount);
  //print_matrix(matrix);
  matrix = normalize_matrix(matrix);
  //print_matrix(matrix);

  image_convolution(img, matrix, 1.0f, 0.0f);
  gcode_comment("image_unsharpen: " + amount);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_blurr(PImage img) {
  // Basic blur matrix

  float[][] matrix = { { 1, 1, 1 },
                       { 1, 1, 1 },
                       { 1, 1, 1 } }; 
  
  matrix = normalize_matrix(matrix);
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_sharpen(PImage img) {
  // Simple sharpen matrix

  float[][] matrix = { {  0, -1,  0 },
                       { -1,  5, -1 },
                       {  0, -1,  0 } }; 
  
  //print_matrix(matrix);
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_emboss(PImage img) {
  float[][] matrix = { { -2, -1,  0 },
                       { -1,  1,  1 },
                       {  0,  1,  2 } }; 
                       
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_edge_detect(PImage img) {
  // Edge detect
  float[][] matrix = { {  0,  1,  0 },
                       {  1, -4,  1 },
                       {  0,  1,  0 } }; 
                       
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_motion_blur(PImage img) {
  // Motion Blur
  // http://lodev.org/cgtutor/filtering.html
                       
  float[][] matrix = { {  1, 0, 0, 0, 0, 0, 0, 0, 0 },
                       {  0, 1, 0, 0, 0, 0, 0, 0, 0 },
                       {  0, 0, 1, 0, 0, 0, 0, 0, 0 },
                       {  0, 0, 0, 1, 0, 0, 0, 0, 0 },
                       {  0, 0, 0, 0, 1, 0, 0, 0, 0 },
                       {  0, 0, 0, 0, 0, 1, 0, 0, 0 },
                       {  0, 0, 0, 0, 0, 0, 1, 0, 0 },
                       {  0, 0, 0, 0, 0, 0, 0, 1, 0 },
                       {  0, 0, 0, 0, 0, 0, 0, 0, 1 } };

  matrix = normalize_matrix(matrix);
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_outline(PImage img) {
  // Outline (5x5)
  // https://www.jmicrovision.com/help/v125/tools/classicfilterop.htm

  float[][] matrix = { { 1,  1,   1,  1,  1 },
                       { 1,  0,   0,  0,  1 },
                       { 1,  0, -16,  0,  1 },
                       { 1,  0,   0,  0,  1 },
                       { 1,  1,   1,  1,  1 } };
                       
  //matrix = normalize_matrix(matrix);
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_sobel(PImage img, float factor, float bias) {

  // Looks like some kind of inverting edge detection
  //float[][] matrix = { { -1, -1, -1 },
  //                     { -1,  8, -1 },
  //                     { -1, -1, -1 } }; 
                       
  //float[][] matrix = { {  1,  2,   0,  -2,  -1 },
  //                     {  4,  8,   0,  -8,  -4 },
  //                     {  6, 12,   0, -12,  -6 },
  //                     {  4,  8,   0,  -8,  -4 },
  //                     {  1,  2,   0,  -2,  -1 } };
  
  // Sobel 3x3 X
  float[][] matrixX = { { -1,  0,  1 },
                        { -2,  0,  2 },
                        { -1,  0,  1 } }; 

  // Sobel 3x3 Y
  float[][] matrixY = { { -1, -2, -1 },
                        {  0,  0,  0 },
                        {  1,  2,  1 } }; 
  
  image_convolution(img, matrixX, factor, bias);
  image_convolution(img, matrixY, factor, bias);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void image_convolution(PImage img, float[][] matrix, float factor, float bias) {
  // What about edge pixels?  Ignoring (maxrixsize-1)/2 pixels on the edges?
  
  int n = matrix.length;      // matrix rows
  int m = matrix[0].length;   // matrix columns
  
  //print_matrix(matrix);
  
  PImage simg = createImage(img.width, img.height, RGB);
  simg.copy(img, 0, 0, img.width, img.height, 0, 0, simg.width, simg.height);
  int matrixsize = matrix.length;

  for (int x = 0; x < simg.width; x++) {
    for (int y = 0; y < simg.height; y++ ) {
      int c = convolution(x, y, matrix, matrixsize, simg, factor, bias);
      int loc = x + y*simg.width;
      img.pixels[loc] = c;
    }
  }
  updatePixels();
}


///////////////////////////////////////////////////////////////////////////////////////////////////////
// Source:  https://py.processing.org/tutorials/pixels/
// By: Daniel Shiffman
// Factor & bias added by SCC

public int convolution(int x, int y, float[][] matrix, int matrixsize, PImage img, float factor, float bias) {
  float rtotal = 0.0f;
  float gtotal = 0.0f;
  float btotal = 0.0f;
  int offset = matrixsize / 2;

  // Loop through convolution matrix
  for (int i = 0; i < matrixsize; i++) {
    for (int j= 0; j < matrixsize; j++) {
      // What pixel are we testing
      int xloc = x+i-offset;
      int yloc = y+j-offset;
      int loc = xloc + img.width*yloc;
      // Make sure we have not walked off the edge of the pixel array
      loc = constrain(loc,0,img.pixels.length-1);
      // Calculate the convolution
      // We sum all the neighboring pixels multiplied by the values in the convolution matrix.
      rtotal += (red(img.pixels[loc]) * matrix[i][j]);
      gtotal += (green(img.pixels[loc]) * matrix[i][j]);
      btotal += (blue(img.pixels[loc]) * matrix[i][j]);
    }
  }
  
  // Added factor and bias
  rtotal = (rtotal * factor) + bias;
  gtotal = (gtotal * factor) + bias;
  btotal = (btotal * factor) + bias;
  
  // Make sure RGB is within range
  rtotal = constrain(rtotal,0,255);
  gtotal = constrain(gtotal,0,255);
  btotal = constrain(btotal,0,255);
  // Return the resulting color
  return color(rtotal,gtotal,btotal);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public float [][] multiply_matrix (float[][] matrixA, float[][] matrixB) {
  // Source:  https://en.wikipedia.org/wiki/Matrix_multiplication_algorithm
  // Test:    http://www.calcul.com/show/calculator/matrix-multiplication_;2;3;3;5
  
  int n = matrixA.length;      // matrixA rows
  int m = matrixA[0].length;   // matrixA columns
  int p = matrixB[0].length;

  float[][] matrixC;
  matrixC = new float[n][p]; 

  for (int i=0; i<n; i++) {
    for (int j=0; j<p; j++) {
      for (int k=0; k<m; k++) {
        matrixC[i][j] = matrixC[i][j] + matrixA[i][k] * matrixB[k][j];
      }
    }
  }

  //print_matrix(matrix);
  return matrixC;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public float [][] normalize_matrix (float[][] matrix) {
  // Source:  https://www.taylorpetrick.com/blog/post/convolution-part2
  // The resulting matrix is the same size as the original, but the output range will be constrained 
  // between 0.0 and 1.0.  Useful for keeping brightness the same.
  // Do not use on a maxtix that sums to zero, such as sobel.
  
  int n = matrix.length;      // rows
  int m = matrix[0].length;   // columns
  float sum = 0;
  
  for (int i=0; i<n; i++) {
    for (int j=0; j<m; j++) {
      sum += matrix[i][j];
    }
  }
  
  for (int i=0; i<n; i++) {
    for (int j=0; j<m; j++) {
      matrix[i][j] = matrix[i][j] / abs(sum);
    }
  }
  
  return matrix;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public float [][] scale_matrix(float[][] matrix, int scale) {
  int n = matrix.length;      // rows
  int p = matrix[0].length;   // columns
  float sum = 0;
                         
  float [][] nmatrix = new float[n*scale][p*scale];
  
  for (int i=0; i<n; i++){
    for (int j=0; j<p; j++){
      for (int si=0; si<scale; si++){
        for (int sj=0; sj<scale; sj++){
          //println(si, sj);
          int a1 = (i*scale)+si;
          int a2 = (j*scale)+sj;
          float a3 = matrix[i][j];
          //println( a1 + ", " + a2 + " = " + a3 );
          //nmatrix[(i*scale)+si][(j*scale)+sj] = matrix[i][j];
          nmatrix[a1][a2] = a3;
        }
      }
    }
    //println();
  }
  //println("scale_matrix: " + scale);
  return nmatrix;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void print_matrix(float[][] matrix) {
  int n = matrix.length;      // rows
  int p = matrix[0].length;   // columns
  float sum = 0;

  for (int i=0; i<n; i++){
    for (int j=0; j<p; j++){
      sum += matrix[i][j];
      System.out.printf("%10.5f ", matrix[i][j]);
    }
    println();
  }
  println("Sum: ", sum);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////
// A class to check the upper and lower limits of a value
class Limit {
  float min = 2147483647;
  float max = -2147483648;
  
  Limit() { }
  
  public void update_limit(float value_) {
    if (value_ < min) { min = value_; }
    if (value_ > max) { max = value_; }
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
public void grid() {
  // This will give you a rough idea of the size of the printed image, in "grid_scale" units.
  // Some screen scales smaller than 1.0 will sometimes display every other line
  // It looks like a big logic bug, but it just can't display a one pixel line scaled down well.
  
  blendMode(BLEND);
  if (is_grid_on) {
    int image_center_x = PApplet.parseInt(img.width / 2);
    int image_center_y = PApplet.parseInt(img.height / 2);
    int gridlines = 100;
    
    // Give everything outside the paper area a light grey color
    noStroke();
    fill(0, 0, 0, 32);
    float border_x = (paper_size_x - image_size_x) / 2;
    float border_y = (paper_size_y - image_size_y) / 2;
    rect(-border_x/gcode_scale, -border_y/gcode_scale, 999999, -999999);
    rect((image_size_x+border_x)/gcode_scale, -border_y/gcode_scale, 999999, 999999);
    rect((image_size_x+border_x)/gcode_scale, (image_size_y+border_y)/gcode_scale, -999999, 999999);
    rect(-border_x/gcode_scale, (image_size_y+border_y)/gcode_scale, -999999, -999999);

    // Vertical lines
    strokeWeight(1);
    stroke(255, 64, 64, 80);
    noFill();
    for (int x = -gridlines; x <= gridlines; x++) {
      int x0 = PApplet.parseInt(x * grid_scale / gcode_scale);
      line(x0 + image_center_x, -999999, x0 + image_center_x, 999999);
    }
  
    // Horizontal lines
    for (int y = -gridlines; y <= gridlines; y++) {
      int y0 = PApplet.parseInt(y * grid_scale / gcode_scale);
      line(-999999, y0 + image_center_y, 999999, y0 + image_center_y);
    }
    
    // Screen center line
    stroke(255, 64, 64, 80);
    strokeWeight(4);
    line(image_center_x, -999999, image_center_x, 999999);
    line(-999999, image_center_y, 999999, image_center_y);
    strokeWeight(1);
  
    hint(DISABLE_DEPTH_TEST);      // Allow fills to be shown on top.
    
    // Mark the edge of the drawing/image area in blue
    stroke(64, 64, 255, 92);
    noFill();
    strokeWeight(2);
    rect(0, 0, img.width, img.height);
            
    // Green pen origin (home position) dot.
    stroke(0, 255, 0, 255);
    fill(0, 255, 0, 255);
    ellipse(-gcode_offset_x / gcode_scale, -gcode_offset_y / gcode_scale, 10, 10);
    
    // Red center of image dot
    stroke(255, 0, 0, 255);
    fill(255, 0, 0, 255);
    ellipse(image_center_x, image_center_y, 10, 10);
    
    // Blue dot at image 0,0
    stroke(0, 0, 255, 255);
    fill(0, 0, 255, 255);
    ellipse(0, 0, 10, 10);

    hint(ENABLE_DEPTH_TEST);
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
// Experimental, mark coordinates of mouse locations to console.
// Useful for locating vanishing points etc.
// Currently works correctly with screen_scale, translation and rotation.
public void mouse_point() {
  
  print("Mouse point: ");
  switch(screen_rotate) {
    case 0: 
      println(  (mouseX/screen_scale - mx) + ", " +  (mouseY/screen_scale - my) );
      break;
    case 1: 
      println(  (mouseY/screen_scale - my) + ", " + -(mouseX/screen_scale - mx) );
      break;
    case 2: 
      println( -(mouseX/screen_scale - mx) + ", " + -(mouseY/screen_scale - my) );
      break;
    case 3: 
      println( -(mouseY/screen_scale - my) + ", " +  (mouseX/screen_scale - mx) );
      break;
   }
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////
// This path finding module is the basis for nearly all my drawings.
// Find the darkest average line away from my current location and move there.
///////////////////////////////////////////////////////////////////////////////////////////////////////

class PFM_original implements pfm {

  final int    squiggle_length = 500;      // How often to lift the pen
  final int    adjustbrightness = 10;       // How fast it moves from dark to light, over-draw
  final float  desired_brightness = 250;   // How long to process.  You can always stop early with "s" key
  final int    squiggles_till_first_change = 190;

  int          tests = 13;                 // Reasonable values:  13 for development, 720 for final
  int          line_length = PApplet.parseInt(random(3, 40));  // Reasonable values:  3 through 100

  int          squiggle_count;
  int          darkest_x;
  int          darkest_y;
  float        darkest_value;
  float        darkest_neighbor = 256;

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void pre_processing() {
    image_crop();
    image_scale(PApplet.parseInt(image_size_x / pen_width));
    //image_sharpen(img);
    //image_blurr(img);
    //image_unsharpen(img, 5);
    image_unsharpen(img, 4);
    image_unsharpen(img, 3);
    //image_unsharpen(img, 2);
    //image_unsharpen(img, 1);
    //image_motion_blur(img);
    //image_outline(img);
    //image_edge_detect(img);
    //image_sobel(img, 1.0, 0);
    //image_posterize(6);
    //image_erode();
    //image_dilate();
    //image_invert();
    //image_blur(2);
    image_boarder("b1.png", 0, 0);
    image_boarder("b11.png", 0, 0);
    image_desaturate();
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void find_path() {
    find_squiggle();
    if (avg_imgage_brightness() > desired_brightness ) {
      state++;
    }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  private void find_squiggle() {
    int x, y;
  
    //find_darkest();
    find_darkest_area();
    x = darkest_x;
    y = darkest_y;
    squiggle_count++;
    pen_color = 0;
  
    find_darkest_neighbor(x, y);
    move_abs(0, darkest_x, darkest_y);
    pen_down();
    
    for (int s = 0; s < squiggle_length; s++) {
      find_darkest_neighbor(x, y);
      bresenham_lighten(x, y, darkest_x, darkest_y, adjustbrightness);
      move_abs(0, darkest_x, darkest_y);
      x = darkest_x;
      y = darkest_y;
    }
    pen_up();
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  private void find_darkest() {
    darkest_value = 257;
    int darkest_loc = 0;
    
    for (int loc=0; loc < img.width * img.height; loc++) {
      float r = brightness(img.pixels[loc]);
      if (r < darkest_value) {
        darkest_value = r + random(1);
        darkest_loc = loc;
      }
    }
    darkest_x = darkest_loc % img.width;
    darkest_y = (darkest_loc-darkest_x) / img.width;
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  private void find_darkest_area() {
    // Warning, Experimental: 
    // Finds the darkest square area by down sampling the img into a much smaller area then finding 
    // the darkest pixel within that.  It returns a random pixel within that darkest area.
    
    int area_size = 10;
    darkest_value = 999;
    int darkest_loc = 1;
    
    PImage img2;
    img2 = createImage(img.width / area_size, img.height / area_size, RGB);
    img2.copy(img, 0, 0, img.width, img.height, 0, 0, img2.width, img2.height);

    for (int loc=0; loc < img2.width * img2.height; loc++) {
      float r = brightness(img2.pixels[loc]);
      
      if (r < darkest_value) {
        darkest_value = r + random(1);
        darkest_loc = loc;
      }
    }
    darkest_x = darkest_loc % img2.width;
    darkest_y = (darkest_loc - darkest_x) / img2.width;
    darkest_x = darkest_x * area_size + PApplet.parseInt(random(area_size));
    darkest_y = darkest_y * area_size + PApplet.parseInt(random(area_size));
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  private void find_darkest_neighbor(int start_x, int start_y) {
    darkest_neighbor = 257;
    float delta_angle;
    float start_angle;
    
    //start_angle = random(-35, -15) + cos(radians(start_x/4+(start_y/6)))*30;
    //start_angle = random(-95, -75) + cos(radians(start_y/15))*90;
    //start_angle = 36 + degrees( ( sin(radians(start_x/9+46)) + cos(radians(start_y/26+26)) ));
    //start_angle = 34 + degrees( ( sin(radians(start_x/9+46)) + cos(radians(start_y/-7+26)) ));
    //if (squiggle_count <220) { tests = 20; } else { tests = 2; }
    //start_angle = random(20, 1);       // Cuba 1
    start_angle = random(-72, -52);    // Spitfire
    //start_angle = random(-120, -140);  // skier
    //start_angle = random(-360, -1);    // gradiant magic
    //start_angle = squiggle_count % 360;
    //start_angle += squiggle_count/4;
    //start_angle = -45;
    //start_angle = (squiggle_count * 37) % 360;
    
    //delta_angle = 180 + 10 / (float)tests;
    //delta_angle = 360.0 / (float)tests;

    if (squiggle_count < squiggles_till_first_change) { 
      //line_length = int(random(3, 60));
      delta_angle = 360.0f / (float)tests;
    } else {
      //start_angle = degrees(atan2(img.height/2.0 - start_y -470, img.width/2.0 - start_x+130) )-10+90;    // wierd spiral
      //start_angle = degrees(atan2(img.height/2.0 - start_y +145, img.width/2.0 - start_x+45) )-10+90;    //cuba car
      //start_angle = degrees(atan2(img.height/2.0 - start_y +210, img.width/2.0 - start_x-100) )-10;    // italy
      delta_angle = 180 + 7 / (float)tests;
    }
    
    for (int d=0; d<tests; d++) {
      float b = bresenham_avg_brightness(start_x, start_y, line_length, (delta_angle * d) + start_angle);
    }
  }
  
  ///////////////////////////////////////////////////////////////////////////////////////////////////////
  public float bresenham_avg_brightness(int x0, int y0, float distance, float degree) {
    int x1, y1;
    int sum_brightness = 0;
    int count_brightness = 0;
    ArrayList <intPoint> pnts;
    
    x1 = PApplet.parseInt(cos(radians(degree))*distance) + x0;
    y1 = PApplet.parseInt(sin(radians(degree))*distance) + y0;
    x0 = constrain(x0, 0, img.width-1);
    y0 = constrain(y0, 0, img.height-1);
    x1 = constrain(x1, 0, img.width-1);
    y1 = constrain(y1, 0, img.height-1);
    
    pnts = bresenham(x0, y0, x1, y1);
    for (intPoint p : pnts) {
      int loc = p.x + p.y*img.width;
      sum_brightness += brightness(img.pixels[loc]);
      count_brightness++;
      if (sum_brightness / count_brightness < darkest_neighbor) {
        darkest_x = p.x;
        darkest_y = p.y;
        darkest_neighbor = (float)sum_brightness / (float)count_brightness;
      }
      //println(x0+","+y0+"  "+p.x+","+p.y+"  brightness:"+sum_brightness / count_brightness+"  darkest:"+darkest_neighbor+"  "+darkest_x+","+darkest_y); 
    }
    //println();
    return( sum_brightness / count_brightness );
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void post_processing() {
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void output_parameters() {
    gcode_comment("adjustbrightness: " + adjustbrightness);
    gcode_comment("squiggle_length: " + squiggle_length);
  }

}
///////////////////////////////////////////////////////////////////////////////////////////////////////
// Path finding module:  https://github.com/krummrey/SpiralFromImage
//
// Issues:
//    Transparencys currently do not work as a mask colour
///////////////////////////////////////////////////////////////////////////////////////////////////////

class PFM_spiral implements pfm {

  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void pre_processing() {
    image_crop();
    image_scale(1000);
    image_unsharpen(img, 3);
    image_boarder("b6.png", 0, 0);
    image_desaturate();
  }
    
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void find_path() {
    int c = 0;                               // Sampled color
    float b;                                   // Sampled brightness
    float dist = 7;                            // Distance between rings
    float radius = dist/2;                     // Current radius
    float aradius = 1;                         // Radius with brighness applied up
    float bradius = 1;                         // Radius with brighness applied down
    float alpha;                               // Initial rotation
    float density = 75;                        // Density
    float ampScale = 4.5f;                      // Controls the amplitude
    float x, y, xa, ya, xb, yb;                // Current X and Y + jittered X and Y 
    float k;                                   // Current radius
    float endRadius;                           // Largest value the spiral needs to cover the image
    int mask = color (240, 240, 240);        // This color will not be drawn (WHITE)
      
    k = density/radius;
    alpha = k;
    radius += dist/(360/k);
    
    // When have we reached the far corner of the image?
    // TODO: this will have to change if not centered
    endRadius = sqrt(pow((img.width/2), 2)+pow((img.height/2), 2));
  
    // Calculates the first point.  Currently just the center.
    // TODO: Allow for ajustable center
    pen_up();
    x =  radius*cos(radians(alpha))+img.width/2;
    y = -radius*sin(radians(alpha))+img.height/2;
    move_abs(0, x, y);
    xa = 0;
    xb = 0;
    ya = 0;
    yb = 0;
    
    // Have we reached the far corner of the image?
    while (radius < endRadius) {
      k = (density/2)/radius;
      alpha += k;
      radius += dist/(360/k);
      x =  radius*cos(radians(alpha))+img.width/2;
      y = -radius*sin(radians(alpha))+img.height/2;
      
      // Are we within the the image?
      // If so check if the shape is open. If not, open it
      if ((x>=0) && (x<img.width) && (y>0) && (y<img.height)) {
  
        // Get the color and brightness of the sampled pixel
        c = img.get (PApplet.parseInt(x), PApplet.parseInt(y));
        b = brightness(c);
        b = map (b, 0, 255, dist*ampScale, 0);
  
        // Move up according to sampled brightness
        aradius = radius+(b/dist);
        xa =  aradius*cos(radians(alpha))+img.width/2;
        ya = -aradius*sin(radians(alpha))+img.height/2;
  
        // Move down according to sampled brightness
        k = (density/2)/radius;
        alpha += k;
        radius += dist/(360/k);
        bradius = radius-(b/dist);
        xb =  bradius*cos(radians(alpha))+img.width/2;
        yb = -bradius*sin(radians(alpha))+img.height/2;
  
        // If the sampled color is the mask color do not write to the shape
        if (brightness(mask) <= brightness(c)) {
          pen_up();
        } else {
          pen_down();
        }
      } else {
        // We are outside of the image
        pen_up();
      }

      int pen_number = PApplet.parseInt(map(brightness(c), 0, 255, 0, pen_count-1)+0.5f);
      move_abs(pen_number, xa, ya);
      move_abs(pen_number, xb, yb);
    }
    
    pen_up();
    state++;
  }
  
 
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void post_processing() {
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void output_parameters() {
    //gcode_comment("dist: " + dist);
    //gcode_comment("ampScale: " + ampScale);     
  }

}
///////////////////////////////////////////////////////////////////////////////////////////////////////
// This path finding module makes some wavy squares
///////////////////////////////////////////////////////////////////////////////////////////////////////

class PFM_squares implements pfm {

  final int    squiggle_length = 1000;      // How often to lift the pen
  final int    adjustbrightness = 9;        // How fast it moves from dark to light, over-draw
  final float  desired_brightness = 250;    // How long to process.  You can always stop early with "s" key
 
  int          tests = 4;                  // Reasonable values:  13 for development, 720 for final
  int          line_length = 30;           // Reasonable values:  3 through 100
 
  int          squiggle_count;
  int          darkest_x;
  int          darkest_y;
  float        darkest_value;
  float        darkest_neighbor = 256;

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void pre_processing() {
    image_crop();
    image_scale(1000);
    image_unsharpen(img, 3);
    image_boarder("b6.png", 0, 0);
    image_desaturate();
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void find_path() {
    find_squiggle();
    if (avg_imgage_brightness() > desired_brightness ) {
      state++;
    }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  private void find_squiggle() {
    int x, y;
  
    //find_darkest();
    find_darkest_area();
    x = darkest_x;
    y = darkest_y;
    squiggle_count++;
    pen_color = 0;
  
    find_darkest_neighbor(x, y);
    move_abs(0, darkest_x, darkest_y);
    pen_down();
    
    for (int s = 0; s < squiggle_length; s++) {
      find_darkest_neighbor(x, y);
      bresenham_lighten(x, y, darkest_x, darkest_y, adjustbrightness);
      move_abs(0, darkest_x, darkest_y);
      x = darkest_x;
      y = darkest_y;
    }
    pen_up();
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  private void find_darkest() {
    darkest_value = 257;
    int darkest_loc = 0;
    
    for (int loc=0; loc < img.width * img.height; loc++) {
      float r = brightness(img.pixels[loc]);
      if (r < darkest_value) {
        darkest_value = r + random(1);
        darkest_loc = loc;
      }
    }
    darkest_x = darkest_loc % img.width;
    darkest_y = (darkest_loc-darkest_x) / img.width;
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  private void find_darkest_area() {
    // Warning, Experimental: 
    // Finds the darkest square area by down sampling the img into a much smaller area then finding 
    // the darkest pixel within that.  It returns a random pixel within that darkest area.
    
    int area_size = 10;
    darkest_value = 999;
    int darkest_loc = 1;
    
    PImage img2;
    img2 = createImage(img.width / area_size, img.height / area_size, RGB);
    img2.copy(img, 0, 0, img.width, img.height, 0, 0, img2.width, img2.height);

    for (int loc=0; loc < img2.width * img2.height; loc++) {
      float r = brightness(img2.pixels[loc]);
      
      if (r < darkest_value) {
        darkest_value = r + random(1);
        darkest_loc = loc;
      }
    }
    darkest_x = darkest_loc % img2.width;
    darkest_y = (darkest_loc - darkest_x) / img2.width;
    darkest_x = darkest_x * area_size + PApplet.parseInt(random(area_size));
    darkest_y = darkest_y * area_size + PApplet.parseInt(random(area_size));
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  private void find_darkest_neighbor(int start_x, int start_y) {
    darkest_neighbor = 257;
    float start_angle;
    float delta_angle;
    
    start_angle = 36 + degrees( ( sin(radians(start_x/9+46)) + cos(radians(start_y/26+26)) ));
    delta_angle = 360.0f / (float)tests;
    
    for (int d=0; d<tests; d++) {
      float b = bresenham_avg_brightness(start_x, start_y, line_length, (delta_angle * d) + start_angle);
    }
  }
  
  ///////////////////////////////////////////////////////////////////////////////////////////////////////
  public float bresenham_avg_brightness(int x0, int y0, float distance, float degree) {
    int x1, y1;
    int sum_brightness = 0;
    int count_brightness = 0;
    ArrayList <intPoint> pnts;
    
    x1 = PApplet.parseInt(cos(radians(degree))*distance) + x0;
    y1 = PApplet.parseInt(sin(radians(degree))*distance) + y0;
    x0 = constrain(x0, 0, img.width-1);
    y0 = constrain(y0, 0, img.height-1);
    x1 = constrain(x1, 0, img.width-1);
    y1 = constrain(y1, 0, img.height-1);
    
    pnts = bresenham(x0, y0, x1, y1);
    for (intPoint p : pnts) {
      int loc = p.x + p.y*img.width;
      sum_brightness += brightness(img.pixels[loc]);
      count_brightness++;
      if (sum_brightness / count_brightness < darkest_neighbor) {
        darkest_x = p.x;
        darkest_y = p.y;
        darkest_neighbor = (float)sum_brightness / (float)count_brightness;
      }
      //println(x0+","+y0+"  "+p.x+","+p.y+"  brightness:"+sum_brightness / count_brightness+"  darkest:"+darkest_neighbor+"  "+darkest_x+","+darkest_y); 
    }
    //println();
    return( sum_brightness / count_brightness );
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void post_processing() {
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  public void output_parameters() {
    gcode_comment("adjustbrightness: " + adjustbrightness);
    gcode_comment("squiggle_length: " + squiggle_length);
  }

}
  public void settings() {  size(1415, 900, P3D); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "--present", "--window-color=#666666", "--stop-color=#cccccc", "Drawbot_image_to_gcode_v2" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
