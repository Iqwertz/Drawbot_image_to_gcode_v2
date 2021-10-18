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
let scale_factor = 10;
let global_gcode_scale = 0.25; //if 0 scale is auto. calculated
let flip_gcode_xy = true;
let skip_gcode_negative_values = true;
let   paper_size_x = 16 * scale_factor;
let   paper_size_y = 20 * scale_factor;
let   image_size_x = 28 * 15;
let   image_size_y = 36 * 15;
let   paper_top_to_origin = 9;      //mm, make smaller to move drawing down on paper
let   pen_width = 0.65;               //mm, determines image_scale, reduce, if solid black areas are speckled with white holes. //0.65
let     pen_count = 1;
let    gcode_decimal_seperator = '.';    
let     gcode_decimals = 2;             // Number of digits right of the decimal point in the gcode files.
let     svg_decimals = 2;               // Number of digits right of the decimal point in the SVG file.
let   grid_scale = 10.0;              // Use 10.0 for centimeters, 25.4 for inches, and between 444 and 529.2 for cubits.

let input_image_path = "input/ich-removebg-preview.png";


// Every good program should have a shit pile of badly named globals.
//Class cl = null;
let ocl;
//let current_pfm = 0;
//String[] pfms = {"PFM_original", "PFM_squares"}; 

let     state = 1;
let     pen_selected = 0;
let     current_copic_set = 0;
let     display_line_count;
let  display_mode = "drawing";
let  img_orginal;               // The original image
let  img_reference;             // After pre_processing, croped, scaled, boarder, etc.  This is what we will try to draw. 
let  img;                       // Used during drawing for current brightness levels.  Gets damaged during drawing.
let   gcode_offset_x = 0.0;
let   gcode_offset_y = 0.0;
let   gcode_scale = 0.0;
let   screen_scale = 0.0;
let   screen_scale_org = 0.0;
let     screen_rotate = 0;
let   old_x = 0.0;
let   old_y = 0.0;
let     mx = 0;
let     my = 0;
let     morgx = 0;
let     morgy = 0;
let     pen_color = 0;
let is_pen_down = false;
let is_grid_on = false;
let  path_selected = "";
let  file_selected = "";
let  basefile_selected = "";
let     startTime = 0;
let ctrl_down = false;

//Limit   dx, dy;
//PrintWriter OUTPUT;
let d1;

//float[] pen_distribution = new float[pen_count];
function preload() {
  img = loadImage(input_image_path);
  state++;
}

function setup() {
  createCanvas(1415,900,WEBGL);
  colorMode(RGB);
  //randomSeed(millis());
  randomSeed(3);
  d1 = new botDrawing();
  //dx = new Limit(); 
  //dy = new Limit(); 
  //loadInClass(pfms[current_pfm]);
  ocl = new PFM_original();
  //selectInput("Select an image to process:", "fileSelected");
}


function draw() {

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
    d1.evenly_distribute_pen_changes(d1.get_line_count(), pen_count);
    d1.distribute_pen_changes_according_to_percentages(display_line_count, pen_count);

    println("elapsed time: " + (millis() - startTime) / 1000.0 + " seconds");
    display_line_count = d1.line_count;
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
function setup_squiggles() {
  let   gcode_scale_x;
  let   gcode_scale_y;
  let   screen_scale_x;
  let   screen_scale_y;

  //println("setup_squiggles...");

  d1.line_count = 0;
  //randomSeed(millis());
  image_rotate();

  img_orginal = createImage(img.width, img.height, RGB);
  img_orginal.copy(img, 0, 0, img.width, img.height, 0, 0, img.width, img.height);
  
  img.loadPixels();
  console.log(img);
    console.log(img.pixels[10]);

  ocl.pre_processing();
  img_reference = createImage(img.width, img.height, RGB);
  img_reference.copy(img, 0, 0, img.width, img.height, 0, 0, img.width, img.height);
  
  gcode_scale_x = image_size_x / img.width;
  gcode_scale_y = image_size_y / img.height;
  if(global_gcode_scale!=0){
    gcode_scale=global_gcode_scale;
  }else{
    gcode_scale = min(gcode_scale_x, gcode_scale_y);
  }
  gcode_offset_x = 0;//- (img.width * gcode_scale / 2.0);  
  gcode_offset_y = - paper_top_to_origin; // - (paper_size_y - (img.height * gcode_scale)) / 2.0);

  screen_scale_x = width / img.width;
  screen_scale_y = height / img.height;
  screen_scale = min(screen_scale_x, screen_scale_y);
  screen_scale_org = screen_scale;

  state++;
}

/*

///////////////////////////////////////////////////////////////////////////////////////////////////////
void setup() {
  size(1415, 900, P3D);
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
  loadInClass(pfms[current_pfm]);

  loadImageFromPath(input_image_path);
  selectInput("Select an image to process:", "fileSelected");
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
void draw() {
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
    d1.evenly_distribute_pen_changes(d1.get_line_count(), pen_count);
    d1.distribute_pen_changes_according_to_percentages(display_line_count, pen_count);

    println("elapsed time: " + (millis() - startTime) / 1000.0 + " seconds");
    display_line_count = d1.line_count;
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
void loadImageFromPath(path) {
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
void fileSelected(File selection) {
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
void setup_squiggles() {
  float   gcode_scale_x;
  float   gcode_scale_y;
  float   screen_scale_x;
  float   screen_scale_y;

  //println("setup_squiggles...");

  d1.line_count = 0;
  //randomSeed(millis());
  img = loadImage(path_selected, "jpeg");  // Load the image into the program  

  image_rotate();

  img_orginal = createImage(img.width, img.height, RGB);
  img_orginal.copy(img, 0, 0, img.width, img.height, 0, 0, img.width, img.height);

  ocl.pre_processing();
  img.loadPixels();
  img_reference = createImage(img.width, img.height, RGB);
  img_reference.copy(img, 0, 0, img.width, img.height, 0, 0, img.width, img.height);
  
  gcode_scale_x = image_size_x / img.width;
  gcode_scale_y = image_size_y / img.height;
  if(global_gcode_scale!=0){
    gcode_scale=global_gcode_scale;
  }else{
    gcode_scale = min(gcode_scale_x, gcode_scale_y);
  }
  gcode_offset_x = 0;//- (img.width * gcode_scale / 2.0);  
  gcode_offset_y = - paper_top_to_origin; // - (paper_size_y - (img.height * gcode_scale)) / 2.0);

  screen_scale_x = width / (float)img.width;
  screen_scale_y = height / (float)img.height;
  screen_scale = min(screen_scale_x, screen_scale_y);
  screen_scale_org = screen_scale;

  state++;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
void render_all() {
  println("render_all: " + display_mode + ", " + display_line_count + " lines, with pen set " + current_copic_set);
  
  if (display_mode == "drawing") {
    //<d1.render_all();
    d1.render_some(display_line_count);
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
void keyPressed() {
  if (key == 'p') {
    current_pfm ++;
    if (current_pfm >= pfms.length) { current_pfm = 0; }
    //display_line_count = 0;
    loadInClass(pfms[current_pfm]); 
    state = 2;
  }
  if (key == 'g') { 
    create_gcode_files(display_line_count);
  }
  if (key == '<') {
    int delta = -10000;
    display_line_count = int(display_line_count + delta);
    display_line_count = constrain(display_line_count, 0, d1.line_count);
    //println("display_line_count: " + display_line_count);
  }
  if (key == '>') {
    int delta = 10000;
    display_line_count = int(display_line_count + delta);
    display_line_count = constrain(display_line_count, 0, d1.line_count);
    //println("display_line_count: " + display_line_count);
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
  
  d1.distribute_pen_changes_according_to_percentages(display_line_count, pen_count);
  //surface.setSize(img.width, img.height);
  redraw();
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
void set_even_distribution() {
  println("set_even_distribution");
  for (int p = 0; p<pen_count; p++) {
    pen_distribution[p] = display_line_count / pen_count;
    //println("pen_distribution[" + p + "] = " + pen_distribution[p]);
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
void mousePressed() {
  morgx = mouseX - mx; 
  morgy = mouseY - my; 
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
void mouseDragged() {
  mx = mouseX-morgx; 
  my = mouseY-morgy; 
  redraw();
}

*/
