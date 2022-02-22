#!/usr/bin/python3

import cv2 as cv
import numpy as np


SAFE_BORDER = 5 #means %
WORK_SIZE = 1024


def intersection(l1, l2):
    l1p1X, l1p1Y, l1p2X, l1p2Y = l1
    l2p1X, l2p1Y, l2p2X, l2p2Y = l2

    x = ( l2p1X - l1p1X, l2p1Y - l1p1Y )
    d1 = ( l1p2X - l1p1X, l1p2Y - l1p1Y )
    d2 = ( l2p2X - l2p1X, l2p2Y - l2p1Y )

    cross = d1[0] * d2[1] - d1[1] * d2[0]
    if abs(cross) < 0.00001:
        return None

    t1 = ( x[0] * d2[1] - x[1] * d2[0] ) / cross
    return ( l1p1X + d1[0] * t1, l1p1Y + d1[1] * t1 )


def imshow(name, image):
    cv.namedWindow(name, cv.WINDOW_NORMAL)
    cv.imshow(name, image)


def detectPerspective( imageFile ):
    originalImage = cv.imread(imageFile)
    imshow("Original", originalImage)

    width = originalImage.shape[1]
    height = originalImage.shape[0]

    workSize = WORK_SIZE
    safeBorder = workSize * SAFE_BORDER // 100
    xMin = safeBorder
    xMax = workSize - xMin
    yMin = safeBorder
    yMax = workSize - yMin

    image = cv.resize(originalImage, (WORK_SIZE,WORK_SIZE))
    image = cv.blur(image, (3, 3))

    h_lines = []
    v_lines = []

    median = np.median(image)
    minValue = np.min(image)
    maxValue = np.max(image)
    N = 2
    lower = ((N-1)*median + minValue) / N
    upper = ((N-1)*median + maxValue) / N

    edges = cv.Canny( image, lower, upper)

    threshold = 200
    while True:
        h_lines.clear()
        v_lines.clear()

        lines = cv.HoughLinesP(edges, 1, np.pi / 1024, threshold, None, 150, 100)
        if lines is not None:
            for i in range(0, len(lines)):
                line = lines[i][0]
                x1, y1, x2, y2 = line
                dx = abs(x1 - x2)
                dy = abs(y1 - y2)

                min_d = min(dx, dy)
                max_d = max(dx, dy)
                ratio = min_d / max_d

                if ratio > 0.3:
                    continue

                if dx > dy:
                    if y1 >= yMin and y1 <= yMax and y2 >= yMin and y2 <= yMax:
                        h_lines.append((y1, line))
                        #cv.line(image, (line[0], line[1]), (line[2], line[3]), (0,255,255), 3, cv.LINE_AA)
                else:
                    if x1 >= xMin and x1 <= xMax and x2 >= xMin and x2 <= xMax:
                        v_lines.append((x1, line))
                        #cv.line(image, (line[0], line[1]), (line[2], line[3]), (0,255,255), 3, cv.LINE_AA)

            if len(h_lines) >= 2 and len(v_lines) >= 2:
                break

            threshold -= 10
            if threshold < 100:
                break

    if 0 == len(h_lines):
        h_lines.append((yMin, (xMin, yMin, xMax, yMin)))
        h_lines.append((yMax, (xMin, yMax, xMax, yMax)))
    elif 1 == len(h_lines):
        if h_lines[0][0] < (height // 2):
            h_lines.append((yMax, (xMin, yMax, xMax, yMax)))
        else:
            h_lines.append((yMin, (xMin, yMin, xMax, yMin)))
    else:
        h_lines.sort(key = lambda item: item[0])
        h_lines = [h_lines[0], h_lines[-1]]

    if 0 == len(v_lines):
        v_lines.append((xMin, (xMin, yMin, xMin, yMax)))
        v_lines.append((xMax, (xMax, yMin, xMax, yMax)))
    elif 1 == len(v_lines):
        if v_lines[0][0] < (width // 2):
            v_lines.append((xMax, (xMax, yMin, xMax, yMax)))
        else:
            v_lines.append((xMin, (xMin, yMin, xMin, yMax)))
    else:
        v_lines.sort(key = lambda item: item[0])
        v_lines = [v_lines[0], v_lines[-1]]

    # for _, l in h_lines:
    #     cv.line(image, (l[0], l[1]), (l[2], l[3]), (0,0,255), 3, cv.LINE_AA)

    # for _, l in v_lines:
    #     cv.line(image, (l[0], l[1]), (l[2], l[3]), (255,0,0), 3, cv.LINE_AA)

    p1 = intersection( h_lines[0][1], v_lines[0][1] )
    p2 = intersection( h_lines[0][1], v_lines[1][1] )
    p3 = intersection( h_lines[1][1], v_lines[0][1] )
    p4 = intersection( h_lines[1][1], v_lines[1][1] )

    print(p1, p2, p3, p4)

    x1 = (p1[0] + p3[0]) / 2
    y1 = (p1[1] + p2[1]) / 2
    x2 = (p2[0] + p4[0]) / 2
    y2 = (p3[1] + p4[1]) / 2

    if p1 is None or p2 is None or p3 is None or p3 is None:
        print("Failed !")
    else:
        cv.line(image, (int(x1),int(y1)), (int(x2),int(y1)), (128,255,128), 2, cv.LINE_AA)
        cv.line(image, (int(x1),int(y1)), (int(x1),int(y2)), (128,255,128), 2, cv.LINE_AA)
        cv.line(image, (int(x1),int(y2)), (int(x2),int(y2)), (128,255,128), 2, cv.LINE_AA)
        cv.line(image, (int(x2),int(y1)), (int(x2),int(y2)), (128,255,128), 2, cv.LINE_AA)

        cv.line(image, (int(p1[0]), int(p1[1])), (int(p2[0]), int(p2[1]) ), (0,200,0), 2, cv.LINE_AA)
        cv.line(image, (int(p1[0]), int(p1[1])), (int(p3[0]), int(p3[1]) ), (0,200,0), 2, cv.LINE_AA)
        cv.line(image, (int(p2[0]), int(p2[1])), (int(p4[0]), int(p4[1]) ), (0,200,0), 2, cv.LINE_AA)
        cv.line(image, (int(p3[0]), int(p3[1])), (int(p4[0]), int(p4[1]) ), (0,200,0), 2, cv.LINE_AA)

    imshow("Lines", image)

    scaleX = width / WORK_SIZE
    scaleY = height / WORK_SIZE

    p1 = ( int(p1[0] * scaleX), int(p1[1] * scaleY) )
    p2 = ( int(p2[0] * scaleX), int(p2[1] * scaleY) )
    p3 = ( int(p3[0] * scaleX), int(p3[1] * scaleY) )
    p4 = ( int(p4[0] * scaleX), int(p4[1] * scaleY) )

    x1 = int(x1 * scaleX)
    x2 = int(x2 * scaleX)
    y1 = int(y1 * scaleY)
    y2 = int(y2 * scaleY)

    src = np.float32([ p1, p2, p3, p4 ])
    dest = np.float32([ (x1,y1), (x2, y1), (x1, y2), (x2, y2) ])

    M = cv.getPerspectiveTransform(src, dest)

    outputImage = cv.warpPerspective( originalImage, M, (width, height), flags=cv.INTER_LANCZOS4 )
    cv.namedWindow("Output", cv.WINDOW_NORMAL)
    imshow("Output", outputImage)

detectPerspective("1.jpg")
cv.waitKey(0)
