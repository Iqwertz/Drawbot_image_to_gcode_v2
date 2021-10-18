///////////////////////////////////////////////////////////////////////////////////////////////////////
// This path finding module is the basis for nearly all my drawings.
// Find the darkest average line away from my current location and move there.
///////////////////////////////////////////////////////////////////////////////////////////////////////

class PFM_original {

  
  constructor(){
    this.squiggle_length = 5000;      // How often to lift the pen
    this.adjustbrightness = 10;       // How fast it moves from dark to light, over-draw
    this.desired_brightness = 230.0;   // How long to process.  You can always stop early with "s" key
    this.squiggles_till_first_change = 190;
  
    this.tests = 13;                 // Reasonable values:  13 for development, 720 for final
    this.line_length = parseInt(random(3, 40));  // Reasonable values:  3 through 100
  
    this.squiggle_count = 0;
    this.darkest_x = 0;
    this.darkest_y = 0;
    this.darkest_value = 0.0;
    this.darkest_neighbor = 256.0;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  pre_processing() {
    console.log(img);
    image_crop();
    image_scale(parseInt(image_size_x / pen_width));
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
  find_path() {
    this.find_squiggle();
    if (avg_imgage_brightness() > this.desired_brightness ) {
      state++;
    }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  find_squiggle() {
    let x, y;
  
    //this.find_darkest();
    this.find_darkest_area();
    x = this.darkest_x;
    y = this.darkest_y;
    this.squiggle_count++;
    pen_color = 0;
  
    this.find_darkest_neighbor(x, y);
    move_abs(0, this.darkest_x, this.darkest_y);
    pen_down();
    
    for (let s = 0; s < this.squiggle_length; s++) {
      this.find_darkest_neighbor(x, y);
      bresenham_lighten(x, y, this.darkest_x, this.darkest_y, this.adjustbrightness);
      move_abs(0, this.darkest_x, this.darkest_y);
      x = this.darkest_x;
      y = this.darkest_y;
    }
    pen_up();
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  find_darkest() {
    this.darkest_value = 257; //257
    let darkest_loc = 0;
    
    for (let loc=0; loc < img.width * img.height; loc++) {
      let r = brightness(img.pixels[loc]);
      if (r < this.darkest_value) {
        this.darkest_value = r + random(1);
        darkest_loc = loc;
      }
    }
    this.darkest_x = darkest_loc % img.width;
    this.darkest_y = (darkest_loc-this.darkest_x) / img.width;
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  find_darkest_area() {
    // Warning, Experimental: 
    // Finds the darkest square area by down sampling the img into a much smaller area then finding 
    // the darkest pixel within that.  It returns a random pixel within that darkest area.
    
    let area_size = 10;
    this.darkest_value = 999;
    let darkest_loc = 1;
    
    let img2;
    img2 = createImage(img.width / area_size, img.height / area_size, RGB);
    img2.copy(img, 0, 0, img.width, img.height, 0, 0, img2.width, img2.height);

    for (let loc=0; loc < img2.width * img2.height; loc++) {
      let r = brightness(img2.pixels[loc]);
      
      if (r < this.darkest_value) {
        this.darkest_value = r + random(1);
        darkest_loc = loc;
      }
    }
    this.darkest_x = darkest_loc % img2.width;
    this.darkest_y = (darkest_loc - this.darkest_x) / img2.width;
    this.darkest_x = this.darkest_x * area_size + int(random(area_size));
    this.darkest_y = this.darkest_y * area_size + int(random(area_size));
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////
  find_darkest_neighbor(start_x,start_y) {
    this.darkest_neighbor = 257;
    let delta_angle = 0.0;
    let start_angle = 0.0;
    
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

    if (this.squiggle_count < this.squiggles_till_first_change) { 
      //line_length = int(random(3, 60));
      delta_angle = 360.0 / this.tests;
    } else {
      //start_angle = degrees(atan2(img.height/2.0 - start_y -470, img.width/2.0 - start_x+130) )-10+90;    // wierd spiral
      //start_angle = degrees(atan2(img.height/2.0 - start_y +145, img.width/2.0 - start_x+45) )-10+90;    //cuba car
      //start_angle = degrees(atan2(img.height/2.0 - start_y +210, img.width/2.0 - start_x-100) )-10;    // italy
      delta_angle = 180 + 7 / this.tests;
    }
    
    for (let d=0; d<this.tests; d++) {
      let b = this.bresenham_avg_brightness(start_x, start_y, this.line_length, (delta_angle * d) + start_angle);
    }
  }
  
  ///////////////////////////////////////////////////////////////////////////////////////////////////////
  bresenham_avg_brightness( x0, y0, distance, degree) {
    let x1, y1;
    let sum_brightness = 0;
    let count_brightness = 0;
    let pnts = [];
    
    x1 = parseInt(cos(radians(degree))*distance) + x0;
    y1 = parseInt(sin(radians(degree))*distance) + y0;
    x0 = constrain(x0, 0, img.width-1);
    y0 = constrain(y0, 0, img.height-1);
    x1 = constrain(x1, 0, img.width-1);
    y1 = constrain(y1, 0, img.height-1);
    
    pnts = bresenham(x0, y0, x1, y1);
    for (let p of pnts) {
      let loc = p.x + p.y*img.width;
      sum_brightness += brightness(img.pixels[loc]);
      count_brightness++;
      if (sum_brightness / count_brightness < this.darkest_neighbor) {
        this.darkest_x = p.x;
        this.darkest_y = p.y;
        this.darkest_neighbor = sum_brightness / count_brightness;
      }
      //println(x0+","+y0+"  "+p.x+","+p.y+"  brightness:"+sum_brightness / count_brightness+"  darkest:"+this.darkest_neighbor+"  "+this.darkest_x+","+this.darkest_y); 
    }
    //println();
    return( sum_brightness / count_brightness );
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////////////
  post_processing() {
  }

}
