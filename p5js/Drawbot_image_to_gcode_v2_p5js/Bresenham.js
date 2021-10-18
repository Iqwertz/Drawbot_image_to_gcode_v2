///////////////////////////////////////////////////////////////////////////////////////////////////////
class intPoint {
  constructor(x_, y_) {
    this.x = x_;
    this.y = y_;
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
// Algorithm was developed by Jack Elton Bresenham in 1962
// http://en.wikipedia.org/wiki/Bresenham's_line_algorithm
// Traslated from pseudocode labled "Simplification" from the link above.
///////////////////////////////////////////////////////////////////////////////////////////////////////
function bresenham(x0, y0, x1, y1) {
  let sx, sy;
  let err;
  let e2;
  let pnts = [];

  let dx = abs(x1-x0);
  let dy = abs(y1-y0);
  if (x0 < x1) { sx = 1; } else { sx = -1; }
  if (y0 < y1) { sy = 1; } else { sy = -1; }
  err = dx-dy;
  while (true) {
    pnts.push(new intPoint(x0, y0));
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
  function bresenham_lighten(x0, y0, x1, y1, _adjustbrightness) {
    let pnts;
  
    pnts = bresenham(x0, y0, x1, y1);
    for (let p of pnts) {
      lighten_one_pixel(_adjustbrightness * 5, p.x, p.y);
    }
  }

///////////////////////////////////////////////////////////////////////////////////////////////////////
