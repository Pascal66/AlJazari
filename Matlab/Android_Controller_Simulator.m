clear all
close all
clc

s = serial('COM3', 'BaudRate', 115200);
fopen(s)
th = 128;
while(1)
%     th = th + 1;
%     fprintf(s,'$');
    fwrite(s,uint8(36));
    fwrite(s,uint8(th));
    fwrite(s,uint8(th));
    fwrite(s,uint8(th));
    fwrite(s,uint8(th));
    fwrite(s,uint8(th));
    [m,n] = fread(s);
    if(n>0)
        uint8(m)
    end
end