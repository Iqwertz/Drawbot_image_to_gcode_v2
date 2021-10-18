///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_threshold() {
  img.filter(THRESHOLD);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_desaturate() {
  img.filter(GRAY);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_invert() {
  img.filter(INVERT);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_posterize(amount) {
  img.filter(POSTERIZE, amount);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_blur(amount) {
  img.filter(BLUR, amount);
}
 
///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_erode() {
  img.filter(ERODE);
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_dilate() {
  img.filter(DILATE);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_rotate() {
  //image[y][x]                                     // assuming this is the original orientation
  //image[x][original_width - y]                    // rotated 90 degrees ccw
  //image[original_height - x][y]                   // 90 degrees cw
  //image[original_height - y][original_width - x]  // 180 degrees

  if (img.width > img.height) {
    let img2 = createImage(img.height, img.width, RGB);
    img.loadPixels();
    for (let x=1; x<img.width; x++) {
      for (let y=1; y<img.height; y++) {
        let loc1 = x + y*img.width;
        let loc2 = y + (img.width - x) * img2.width;
        img2.pixels[loc2] = img.pixels[loc1];
      }
    }
    img = img2;
    updatePixels();
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function lighten_one_pixel(_adjustbrightness, x, y) {
  let loc = (y)*img.width + x;
  let r = brightness(img.pixels[loc]);
  //r += adjustbrightness;
  r += _adjustbrightness + random(0, 0.01);
  r = constrain(r,0,255);
  let c = color(r);
  img.pixels[loc] = c;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_scale(new_width) {
  if (img.width != new_width) {
    //img.resize(new_width, 0);
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function avg_imgage_brightness() {
  let b = 0.0;

  for (let p=0; p < img.width * img.height; p++) {
    b += brightness(img.pixels[p]);
  }
  
  return(b / (img.width * img.height));
}
  
///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_crop() {
  // This will center crop to the desired image size image_size_x and image_size_y
  
  let img2;
  let desired_ratio = image_size_x / image_size_y;
  let current_ratio = img.width / img.height;
  
  if (current_ratio < desired_ratio) {
    let desired_x = img.width;
    let desired_y = parseInt(img.width / desired_ratio);
    let half_y = (img.height - desired_y) / 2;
    img2 = createImage(desired_x, desired_y, RGB);
    img2.copy(img, 0, half_y, desired_x, desired_y, 0, 0, desired_x, desired_y);
  } else {
    let desired_x = parseInt(img.height * desired_ratio);
    let desired_y = img.height;
    let half_x = (img.width - desired_x) / 2;
    img2 = createImage(desired_x, desired_y, RGB);
    img2.copy(img, half_x, 0, desired_x, desired_y, 0, 0, desired_x, desired_y);
  }

  img = img2;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_boarder(fname,shrink,blur) {
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
  let temp_boarder = loadImage("boarder/" + fname);
  temp_boarder.resize(img.width, img.height);
  temp_boarder.filter(GRAY);
  temp_boarder.filter(INVERT);
  temp_boarder.filter(BLUR, blur);
  
  //boarder.copy(temp_boarder, 0, 0, temp_boarder.width, temp_boarder.height, 0, 0, boarder.width, boarder.height);
  img.blend(temp_boarder, shrink, shrink, img.width, img.height,  0, 0, img.width, img.height, ADD); 
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_unsharpen(_img, amount) {
  // Source:  https://www.taylorpetrick.com/blog/post/convolution-part3
  // Subtle unsharp matrix
  let matrix = [[ -0.00391, -0.01563, -0.02344, -0.01563, -0.00391 ],
                       [ -0.01563, -0.06250, -0.09375, -0.06250, -0.01563 ],
                       [ -0.02344, -0.09375,  1.85980, -0.09375, -0.02344 ],
                       [ -0.01563, -0.06250, -0.09375, -0.06250, -0.01563 ],
                       [ -0.00391, -0.01563, -0.02344, -0.01563, -0.00391 ] ];
  
  
  //print_matrix(matrix);
  matrix = scale_matrix(matrix, amount);
  //print_matrix(matrix);
  matrix = normalize_matrix(matrix);
  //print_matrix(matrix);
console.log(_img);
  image_convolution(_img, matrix, 1.0, 0.0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_blurr(img) {
  // Basic blur matrix

  let matrix = [[ 1, 1, 1  ],
                [ 1, 1, 1 ],
                [ 1, 1, 1 ]]; 
  
  matrix = normalize_matrix(matrix);
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_sharpen(img) {
  // Simple sharpen matrix

  let matrix = [[  0, -1,  0 ],
                       [ -1,  5, -1 ],
                       [ 0, -1,  0 ]]; 
  
  //print_matrix(matrix);
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_emboss(img) {
  let matrix = [ [ -2, -1,  0 ],
                       [ -1,  1,  1 ],
                       [  0,  1,  2 ] ]; 
                       
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_edge_detect(img) {
  // Edge detect
  let matrix = [ [  0,  1,  0 ],
                       [  1, -4,  1 ],
                       [  0,  1,  0 ] ]; 
                       
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_motion_blur(img) {
  // Motion Blur
  // http://lodev.org/cgtutor/filtering.html
                       
  let matrix = [ [  1, 0, 0, 0, 0, 0, 0, 0, 0 ],
                       [  0, 1, 0, 0, 0, 0, 0, 0, 0 ],
                       [  0, 0, 1, 0, 0, 0, 0, 0, 0 ],
                       [  0, 0, 0, 1, 0, 0, 0, 0, 0 ],
                       [  0, 0, 0, 0, 1, 0, 0, 0, 0 ],
                       [  0, 0, 0, 0, 0, 1, 0, 0, 0 ],
                       [  0, 0, 0, 0, 0, 0, 1, 0, 0 ],
                       [  0, 0, 0, 0, 0, 0, 0, 1, 0 ],
                       [  0, 0, 0, 0, 0, 0, 0, 0, 1 ] ];

  matrix = normalize_matrix(matrix);
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_outline(img) {
  // Outline (5x5)
  // https://www.jmicrovision.com/help/v125/tools/classicfilterop.htm

  let matrix = [ [ 1,  1,   1,  1,  1 ],
                       [ 1,  0,   0,  0,  1 ],
                       [ 1,  0, -16,  0,  1 ],
                       [ 1,  0,   0,  0,  1 ],
                       [ 1,  1,   1,  1,  1 ] ];
                       
  //matrix = normalize_matrix(matrix);
  image_convolution(img, matrix, 1, 0);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_sobel(img, factor, bias) {
  let matrixX = [ [ -1,  0,  1 ],
                [ -2,  0,  2 ],
                [ -1,  0,  1 ] ]; 

  // Sobel 3x3 Y
  let matrixY = [ [ -1, -2, -1 ],
                [  0,  0,  0 ],
                [  1,  2,  1 ] ]; 
  
  image_convolution(img, matrixX, factor, bias);
  image_convolution(img, matrixY, factor, bias);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function image_convolution(_img, matrix, factor, bias) {
  // What about edge pixels?  Ignoring (maxrixsize-1)/2 pixels on the edges?
  
  let n = matrix.length;      // matrix rows
  let m = matrix[0].length;   // matrix columns
  
 // console.log(_img);
  let simg = createImage(_img.width, _img.height, RGB);
  simg.copy(_img, 0, 0, _img.width, _img.height, 0, 0, simg.width, simg.height);
  let matrixsize = matrix.length;
  
  console.log(simg.width, simg.height);
 simg.loadPixels();
  for (let x = 0; x < simg.width; x++) {
    for (let y = 0; y < simg.height; y++ ) {
      let c = convolution(x, y, matrix, matrixsize, simg, factor, bias);
      //console.log(c);
      let loc = x + y*simg.width;
      _img.pixels[loc] = c;
      
    }
  }
  console.log("fin");
  updatePixels();
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
// Source:  https://py.processing.org/tutorials/pixels/
// By: Daniel Shiffman
// Factor & bias added by SCC

function convolution(x,y, matrix, matrixsize, _img,factor,bias) {
  let rtotal = 0.0;
  let gtotal = 0.0;
  let btotal = 0.0;
  let offset = parseInt(matrixsize / 2);
  
  //console.log(matrixsize);

  // Loop through convolution matrix
  
  for (let i = 0; i < matrixsize; i++) {
    for (let j = 0; j < matrixsize; j++) {
      // What pixel are we testing
      let xloc = x+i-offset;
      let yloc = y+j-offset;
      let loc = xloc + _img.width*yloc;
      // Make sure we have not walked off the edge of the pixel array
      loc = constrain(loc,0,_img.pixels.length-1);
      // Calculate the convolution
      // We sum all the neighboring pixels multiplied by the values in the convolution matrix.
     
      //console.log(_img, loc);
      //console.log(_img.pixels[loc]);
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
function multiply_matrix (matrixA,matrixB) {
  // Source:  https://en.wikipedia.org/wiki/Matrix_multiplication_algorithm
  // Test:    http://www.calcul.com/show/calculator/matrix-multiplication_;2;3;3;5
  
  let n = matrixA.length;      // matrixA rows
  let m = matrixA[0].length;   // matrixA columns
  let = matrixB[0].length;

  let matrixC = [[]];

  for (let i=0; i<n; i++) {
    for (let j=0; j<p; j++) {
      for (let k=0; k<m; k++) {
        matrixC[i][j] = matrixC[i][j] + matrixA[i][k] * matrixB[k][j];
      }
    }
  }

  //print_matrix(matrix);
  return matrixC;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function normalize_matrix (matrix) {
  // Source:  https://www.taylorpetrick.com/blog/post/convolution-part2
  // The resulting matrix is the same size as the original, but the output range will be constrained 
  // between 0.0 and 1.0.  Useful for keeping brightness the same.
  // Do not use on a maxtix that sums to zero, such as sobel.
  
  let n = matrix.length;      // rows
  let m = matrix[0].length;   // columns
  let sum = 0;
  
  for (let i=0; i<n; i++) {
    for (let j=0; j<m; j++) {
      sum += matrix[i][j];
    }
  }
  
  for (let i=0; i<n; i++) {
    for (let j=0; j<m; j++) {
      matrix[i][j] = matrix[i][j] / abs(sum);
    }
  }
  
  return matrix;
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
function scale_matrix(matrix, scale) {
  console.log(matrix);
  
  let n = matrix.length;      // rows
  let p = matrix[0].length;   // columns
  let sum = 0.0;
                         
  let nmatrix = [...Array(n*scale)].map(e => Array(p*scale));
  
  for (let i=0; i<n; i++){
    for (let j=0; j<p; j++){
      for (let si=0; si<scale; si++){
        for (let sj=0; sj<scale; sj++){
          //println(si, sj);
          let a1 = (i*scale)+si;
          let a2 = (j*scale)+sj;
          let a3 = matrix[i][j];
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
